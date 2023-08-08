//! Database operations and transactions

use core::time::Duration;
use std::path::{Path, PathBuf};

use anyhow::{anyhow, bail, Context as _, Result};
use diesel::{sqlite::SqliteConnection, Connection};
use diesel_migrations::{embed_migrations, EmbeddedMigrations, MigrationHarness};
use log::{debug, info};
use parking_lot::{MappedMutexGuard, Mutex, MutexGuard};
use url::Url;

use self::{
    error::*,
    op::{Context, Operation, WritableConn},
    operator::Operator,
    ops::*,
};
use crate::models::{
    changelog::{Change, ChangelogEntry},
    coto::{Coto, Cotonoma, NewCoto},
    node::{child::ChildNode, local::LocalNode, BelongsToNode, Node, Principal},
    Id,
};

pub mod error;
pub mod op;
pub mod operator;
pub mod ops;
pub mod sqlite;

/////////////////////////////////////////////////////////////////////////////
// Database
/////////////////////////////////////////////////////////////////////////////

/// A Cotoami database instance based on SQLite
pub struct Database {
    /// The root directory of this database
    root_dir: PathBuf,

    /// Database file URI
    ///
    /// This URI should follow the spec described in the SQLite documentation.
    /// - <https://www.sqlite.org/uri.html>
    /// - <https://www.sqlite.org/c3ref/open.html>
    file_uri: String,

    /// A SqliteConnection for both read and write operations
    ///
    /// To avoid handling possible SQLITE_BUSY on concurrent writes,
    /// it keeps hold of a single read-write connection in the process.
    rw_conn: Mutex<WritableConn>,

    /// Globally shared information
    globals: Mutex<Globals>,
}

impl Database {
    const DATABASE_FILE_NAME: &'static str = "cotoami.db";
    const SQLITE_BUSY_TIMEOUT: Duration = Duration::from_millis(10_000);
    const MIGRATIONS: EmbeddedMigrations = embed_migrations!("migrations");

    pub fn new<P: AsRef<Path>>(root_dir: P) -> Result<Self> {
        let root_dir = root_dir.as_ref().canonicalize()?;
        if !root_dir.is_dir() {
            return Err(DatabaseError::InvalidRootDir(root_dir))?;
        }

        let file_uri = Self::to_file_uri(root_dir.join(Self::DATABASE_FILE_NAME))?;
        let rw_conn = Self::create_rw_conn(&file_uri)?;

        let db = Self {
            root_dir,
            file_uri,
            rw_conn: Mutex::new(rw_conn),
            globals: Mutex::new(Globals::default()),
        };
        db.run_migrations()?;

        db.globals.lock().local_node = db.create_session()?.get_local_node()?.map(|x| x.0);

        info!("Database launched:");
        info!("  root_dir: {}", db.root_dir.display());
        info!("  file_uri: {}", db.file_uri);
        info!("  globals: {:?}", db.globals.lock());

        Ok(db)
    }

    pub fn create_session(&self) -> Result<DatabaseSession<'_>> {
        Ok(DatabaseSession {
            ro_conn: self.create_ro_conn()?,
            get_rw_conn: Box::new(move || self.rw_conn.lock()),
            get_globals: Box::new(move || self.globals.lock()),
        })
    }

    pub fn create_rw_conn(uri: &str) -> Result<WritableConn> {
        let mut rw_conn = SqliteConnection::establish(uri)?;
        sqlite::enable_foreign_key_constraints(&mut rw_conn)?;
        sqlite::enable_wal(&mut rw_conn)?;
        sqlite::set_busy_timeout(&mut rw_conn, Self::SQLITE_BUSY_TIMEOUT)?;
        Ok(WritableConn::new(rw_conn))
    }

    pub fn to_file_uri<P: AsRef<Path>>(path: P) -> Result<String> {
        let path = path.as_ref();
        if path.is_dir() {
            Err(DatabaseError::InvalidFilePath {
                path: path.to_path_buf(),
                reason: "The path is a directory".into(),
            })?
        } else {
            // Url::from_file_path
            // This returns Err if the given path is not absolute or, on Windows,
            // if the prefix is not a disk prefix (e.g. C:) or a UNC prefix (\\).
            // https://docs.rs/url/2.2.2/url/struct.Url.html#method.from_file_path
            let uri = Url::from_file_path(path).or(Err(DatabaseError::InvalidFilePath {
                path: path.to_path_buf(),
                reason: "Invalid path".into(),
            }))?;
            Ok(uri.as_str().to_owned())
        }
    }

    fn run_migrations(&self) -> Result<()> {
        let mut conn = SqliteConnection::establish(&self.file_uri)?;
        conn.run_pending_migrations(Self::MIGRATIONS).unwrap();
        Ok(())
    }

    fn create_ro_conn(&self) -> Result<SqliteConnection> {
        let database_uri = format!("{}?mode=ro&_txlock=deferred", &self.file_uri);
        let ro_conn = SqliteConnection::establish(&database_uri)?;
        Ok(ro_conn)
    }
}

/////////////////////////////////////////////////////////////////////////////
// Globals
/////////////////////////////////////////////////////////////////////////////

/// Global information shared among sessions in a database
#[derive(Debug, Default)]
struct Globals {
    local_node: Option<LocalNode>,
}

/////////////////////////////////////////////////////////////////////////////
// DatabaseSession
/////////////////////////////////////////////////////////////////////////////

pub struct DatabaseSession<'a> {
    ro_conn: SqliteConnection,
    get_rw_conn: Box<dyn Fn() -> MutexGuard<'a, WritableConn> + 'a>,
    get_globals: Box<dyn Fn() -> MutexGuard<'a, Globals> + 'a>,
}

impl<'a> DatabaseSession<'a> {
    /////////////////////////////////////////////////////////////////////////////
    // nodes
    /////////////////////////////////////////////////////////////////////////////

    pub fn get_local_node(&mut self) -> Result<Option<(LocalNode, Node)>> {
        op::run(&mut self.ro_conn, local_node_ops::get())
    }

    pub fn init_as_empty_node(
        &mut self,
        password: Option<&str>,
    ) -> Result<((LocalNode, Node), ChangelogEntry)> {
        op::run_in_transaction(
            &mut (self.get_rw_conn)(),
            |ctx: &mut Context<'_, WritableConn>| {
                let (local_node, node) = local_node_ops::create("", password).run(ctx)?;

                let change = Change::ImportNode(node);
                let changelog = changelog_ops::log_change(&change).run(ctx)?;

                let Change::ImportNode(node) = change else {
                    panic!()
                };
                Ok(((local_node, node), changelog))
            },
        )
        .map(|((local_node, node), changelog)| {
            (self.get_globals)().local_node = Some(local_node.clone());
            ((local_node, node), changelog)
        })
    }

    pub fn init_as_node<'b>(
        &mut self,
        name: &'b str,
        password: Option<&'b str>,
    ) -> Result<((LocalNode, Node), ChangelogEntry)> {
        op::run_in_transaction(
            &mut (self.get_rw_conn)(),
            |ctx: &mut Context<'_, WritableConn>| {
                let (local_node, node) = local_node_ops::create(name, password).run(ctx)?;
                let (cotonoma, coto) = cotonoma_ops::create_root(&node.uuid, name).run(ctx)?;
                let node = node_ops::update_root_cotonoma(&node.uuid, &cotonoma.uuid).run(ctx)?;

                let change = Change::InitNode(node, cotonoma, coto);
                let changelog = changelog_ops::log_change(&change).run(ctx)?;

                let Change::InitNode(node, _, _) = change else {
                    panic!()
                };
                Ok(((local_node, node), changelog))
            },
        )
        .map(|((local_node, node), changelog)| {
            (self.get_globals)().local_node = Some(local_node.clone());
            ((local_node, node), changelog)
        })
    }

    pub fn get_node(&mut self, node_id: &Id<Node>) -> Result<Option<Node>> {
        op::run(&mut self.ro_conn, node_ops::get(node_id))
    }

    pub fn all_nodes(&mut self) -> Result<Vec<Node>> { op::run(&mut self.ro_conn, node_ops::all()) }

    pub fn import_node(&mut self, node: &Node) -> Result<Option<(Node, ChangelogEntry)>> {
        op::run_in_transaction(
            &mut (self.get_rw_conn)(),
            |ctx: &mut Context<'_, WritableConn>| {
                if let Some(node) = node_ops::import(node).run(ctx)? {
                    let change = Change::ImportNode(node);
                    let changelog = changelog_ops::log_change(&change).run(ctx)?;
                    let Change::ImportNode(node) = change else {
                        panic!()
                    };
                    Ok(Some((node, changelog)))
                } else {
                    Ok(None)
                }
            },
        )
    }

    /////////////////////////////////////////////////////////////////////////////
    // node owner
    /////////////////////////////////////////////////////////////////////////////

    pub fn start_owner_session(&mut self, password: &str, duration: Duration) -> Result<LocalNode> {
        let mut local_node = self.require_local_node()?;
        let duration = chrono::Duration::from_std(duration)?;
        local_node
            .start_session(password, duration)
            .context(DatabaseError::AuthenticationFailed)?;
        op::run_in_transaction(
            &mut (self.get_rw_conn)(),
            local_node_ops::update(&local_node),
        )?;
        Ok(local_node.clone())
    }

    pub fn clear_owner_session(&mut self) -> Result<()> {
        let mut local_node = self.require_local_node()?;
        local_node.clear_session();
        op::run_in_transaction(
            &mut (self.get_rw_conn)(),
            local_node_ops::update(&local_node),
        )?;
        Ok(())
    }

    /////////////////////////////////////////////////////////////////////////////
    // child nodes
    /////////////////////////////////////////////////////////////////////////////

    pub fn start_child_session(
        &mut self,
        id: &Id<Node>,
        password: &str,
        duration: Duration,
    ) -> Result<ChildNode> {
        let duration = chrono::Duration::from_std(duration)?;
        op::run_in_transaction(
            &mut (self.get_rw_conn)(),
            |ctx: &mut Context<'_, WritableConn>| {
                let mut child_node = child_node_ops::get_or_err(id)
                    .run(ctx)?
                    // Hide a not-found error for a security reason
                    .context(DatabaseError::AuthenticationFailed)?;
                child_node
                    .start_session(password, duration)
                    .context(DatabaseError::AuthenticationFailed)?;
                child_node = child_node_ops::update(&child_node).run(ctx)?;
                Ok(child_node)
            },
        )
    }

    pub fn clear_child_session(&mut self, id: &Id<Node>) -> Result<()> {
        op::run_in_transaction(
            &mut (self.get_rw_conn)(),
            |ctx: &mut Context<'_, WritableConn>| {
                let mut child_node = child_node_ops::get_or_err(id).run(ctx)??;
                child_node.clear_session();
                child_node_ops::update(&child_node).run(ctx)?;
                Ok(())
            },
        )
    }

    /////////////////////////////////////////////////////////////////////////////
    // operator
    /////////////////////////////////////////////////////////////////////////////

    pub fn local_node_as_operator(&self) -> Result<Operator> {
        let local_node_id = self.require_local_node()?.node_id;
        Ok(Operator::Owner(local_node_id))
    }

    pub fn get_operator_in_session(&mut self, token: &str) -> Result<Option<Operator>> {
        // one of child nodes?
        if let Some(child_node) = op::run(
            &mut self.ro_conn,
            child_node_ops::get_by_session_token(token),
        )? {
            match child_node.verify_session(token) {
                Ok(_) => return Ok(Some(Operator::ChildNode(child_node))),
                Err(e) => debug!("ChildNode: {}", e),
            }
        }

        // the owner of local node?
        let local_node = self.require_local_node()?;
        match local_node.verify_session(token) {
            Ok(_) => return Ok(Some(Operator::Owner(local_node.node_id))),
            Err(e) => debug!("Owner: {}", e),
        }

        Ok(None) // no session
    }

    /////////////////////////////////////////////////////////////////////////////
    // changelog
    /////////////////////////////////////////////////////////////////////////////

    pub fn import_change<'b>(
        &self,
        parent_node_id: &'b Id<Node>,
        log: &'b ChangelogEntry,
    ) -> Result<ChangelogEntry> {
        op::run_in_transaction(
            &mut (self.get_rw_conn)(),
            changelog_ops::import_change(parent_node_id, log),
        )
    }

    /////////////////////////////////////////////////////////////////////////////
    // cotos
    /////////////////////////////////////////////////////////////////////////////

    pub fn get_coto(&mut self, id: &Id<Coto>) -> Result<Option<Coto>> {
        op::run(&mut self.ro_conn, coto_ops::get(id))
    }

    pub fn all_cotos(&mut self) -> Result<Vec<Coto>> { op::run(&mut self.ro_conn, coto_ops::all()) }

    pub fn recent_cotos<'b>(
        &mut self,
        node_id: Option<&'b Id<Node>>,
        posted_in_id: Option<&'b Id<Cotonoma>>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Paginated<Coto>> {
        op::run(
            &mut self.ro_conn,
            coto_ops::recent(node_id, posted_in_id, page_size, page_index),
        )
    }

    /// Posts a coto in the specified cotonoma (`posted_in_id`).
    ///
    /// The target cotonoma has to belong to the local node,
    /// otherwise a change should be made via [Self::import_change()].
    pub fn post_coto<'b>(
        &mut self,
        content: &'b str,
        summary: Option<&'b str>,
        posted_in_id: &'b Id<Cotonoma>,
        operator: &'b Operator,
    ) -> Result<(Coto, ChangelogEntry)> {
        self.ensure_cotonoma_belongs_to_local_node(posted_in_id)?;

        let local_node_id = self.require_local_node()?.node_id;
        let posted_by_id = operator.node_id();
        let new_coto = NewCoto::new(
            &local_node_id,
            posted_in_id,
            &posted_by_id,
            content,
            summary,
        )?;
        op::run_in_transaction(
            &mut (self.get_rw_conn)(),
            |ctx: &mut Context<'_, WritableConn>| {
                let inserted_coto = coto_ops::insert(&new_coto).run(ctx)?;
                let change = Change::CreateCoto(inserted_coto.clone());
                let changelog = changelog_ops::log_change(&change).run(ctx)?;
                Ok((inserted_coto, changelog))
            },
        )
    }

    pub fn edit_coto<'b>(
        &mut self,
        id: &'b Id<Coto>,
        content: &'b str,
        summary: Option<&'b str>,
    ) -> Result<(Coto, ChangelogEntry)> {
        op::run_in_transaction(
            &mut (self.get_rw_conn)(),
            |ctx: &mut Context<'_, WritableConn>| {
                let coto = coto_ops::get_or_err(id).run(ctx)??;
                self.ensure_it_belongs_to_local_node(&coto)?;
                let mut update_coto = coto.to_update();
                update_coto.content = Some(content);
                update_coto.summary = summary;
                let coto = coto_ops::update(&update_coto).run(ctx)?;
                let change = Change::EditCoto {
                    uuid: *id,
                    content: coto.content.clone(),
                    summary: coto.summary.clone(),
                    updated_at: coto.updated_at,
                };
                let changelog = changelog_ops::log_change(&change).run(ctx)?;
                Ok((coto, changelog))
            },
        )
    }

    pub fn delete_coto(&mut self, id: &Id<Coto>) -> Result<ChangelogEntry> {
        op::run_in_transaction(
            &mut (self.get_rw_conn)(),
            |ctx: &mut Context<'_, WritableConn>| {
                let coto = coto_ops::get_or_err(id).run(ctx)??;
                self.ensure_it_belongs_to_local_node(&coto)?;
                if coto_ops::delete(id).run(ctx)? {
                    let change = Change::DeleteCoto(*id);
                    let changelog = changelog_ops::log_change(&change).run(ctx)?;
                    Ok(changelog)
                } else {
                    Err(DatabaseError::not_found(EntityKind::Coto, *id))?
                }
            },
        )
    }

    /////////////////////////////////////////////////////////////////////////////
    // cotonomas
    /////////////////////////////////////////////////////////////////////////////

    pub fn get_cotonoma(&mut self, id: &Id<Cotonoma>) -> Result<Option<(Cotonoma, Coto)>> {
        op::run(&mut self.ro_conn, cotonoma_ops::get(id))
    }

    pub fn all_cotonomas(&mut self) -> Result<Vec<Cotonoma>> {
        op::run(&mut self.ro_conn, cotonoma_ops::all())
    }

    pub fn recent_cotonomas(
        &mut self,
        node_id: Option<&Id<Node>>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Paginated<Cotonoma>> {
        op::run(
            &mut self.ro_conn,
            cotonoma_ops::recent(node_id, page_size, page_index),
        )
    }

    /////////////////////////////////////////////////////////////////////////////
    // internals
    /////////////////////////////////////////////////////////////////////////////

    fn require_local_node(&self) -> Result<MappedMutexGuard<LocalNode>> {
        MutexGuard::try_map((self.get_globals)(), |g| g.local_node.as_mut())
            .map_err(|_| anyhow!("Local node has not yet been created."))
    }

    fn ensure_it_belongs_to_local_node<T: BelongsToNode + std::fmt::Debug>(
        &self,
        entity: &T,
    ) -> Result<()> {
        let local_node_id = self.require_local_node()?.node_id;
        if *entity.node_id() != local_node_id {
            bail!("The entity doesn't belong to the local node: {:?}", entity);
        }
        Ok(())
    }

    fn ensure_cotonoma_belongs_to_local_node<'b>(
        &mut self,
        cotonoma_id: &'b Id<Cotonoma>,
    ) -> Result<()> {
        let (cotonoma, _) = self
            .get_cotonoma(cotonoma_id)?
            .ok_or(DatabaseError::not_found(EntityKind::Cotonoma, *cotonoma_id))?;
        self.ensure_it_belongs_to_local_node(&cotonoma)?;
        Ok(())
    }
}
