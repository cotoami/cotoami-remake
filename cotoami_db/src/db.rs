//! Database operations and transactions

use core::time::Duration;
use std::{
    collections::HashMap,
    path::{Path, PathBuf},
};

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
    coto::{Coto, NewCoto},
    cotonoma::Cotonoma,
    node::{
        child::{ChildNode, NewChildNode},
        local::LocalNode,
        parent::{NewParentNode, ParentNode},
        BelongsToNode, Node, Principal,
    },
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
        db.load_globals()?;

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

    fn load_globals(&self) -> Result<()> {
        let mut db = self.create_session()?;
        let mut globals = self.globals.lock();
        if let Some((local_node, node)) = db.local_node()? {
            globals.local_node = Some(local_node);
            globals.root_cotonoma_id = node.root_cotonoma_id;
        }
        globals.parent_nodes = op::run(&mut db.ro_conn, parent_node_ops::all())?
            .into_iter()
            .map(|p| (p.node_id, p))
            .collect::<HashMap<_, _>>();
        Ok(())
    }
}

/////////////////////////////////////////////////////////////////////////////
// Globals
/////////////////////////////////////////////////////////////////////////////

/// Global information shared among sessions in a database.
/// Most of the fields are cached database rows or column values frequently used internally.
/// For example, [LocalNode] will be used every time when authentication is needed.
#[derive(Debug, Default)]
struct Globals {
    local_node: Option<LocalNode>,
    root_cotonoma_id: Option<Id<Cotonoma>>,
    parent_nodes: HashMap<Id<Node>, ParentNode>,
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
    // local node
    /////////////////////////////////////////////////////////////////////////////

    pub fn local_node(&mut self) -> Result<Option<(LocalNode, Node)>> {
        self.run(local_node_ops::get_pair())
    }

    pub fn is_local<T: BelongsToNode + std::fmt::Debug>(&self, entity: &T) -> bool {
        self.ensure_local(entity).is_ok()
    }

    pub fn is_local_node_initialized(&self) -> bool { self.require_local_node().is_ok() }

    pub fn init_as_node(
        &self,
        name: Option<&str>,
        password: Option<&str>,
    ) -> Result<((LocalNode, Node), ChangelogEntry)> {
        self.transaction(|ctx: &mut Context<'_, WritableConn>| {
            let (local_node, mut node) =
                local_node_ops::create(name.unwrap_or_default(), password).run(ctx)?;

            // Create a root cotonoma if the `name` is not None
            let change = if let Some(name) = name {
                let (node_updated, cotonoma, coto) =
                    node_ops::create_root_cotonoma(&node.uuid, name).run(ctx)?;
                node = node_updated;
                Change::CreateNode(node, Some((cotonoma, coto)))
            } else {
                Change::CreateNode(node, None)
            };

            let changelog = changelog_ops::log_change(&change, &local_node.node_id).run(ctx)?;

            let Change::CreateNode(node, _) = change else { panic!() };
            Ok(((local_node, node), changelog))
        })
        .map(|((local_node, node), changelog)| {
            let mut globals = (self.get_globals)();
            globals.local_node = Some(local_node.clone());
            globals.root_cotonoma_id = node.root_cotonoma_id;
            ((local_node, node), changelog)
        })
    }

    pub fn rename_local_node(&self, name: &str) -> Result<(Node, ChangelogEntry)> {
        let local_node_id = self.require_local_node()?.node_id;
        self.transaction(|ctx: &mut Context<'_, WritableConn>| {
            let updated_at = crate::current_datetime();
            let node = node_ops::rename(&local_node_id, name, Some(updated_at)).run(ctx)?;
            let change = Change::RenameNode {
                uuid: local_node_id,
                name: name.into(),
                updated_at,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((node, changelog))
        })
    }

    /////////////////////////////////////////////////////////////////////////////
    // nodes
    /////////////////////////////////////////////////////////////////////////////

    pub fn node(&mut self, node_id: &Id<Node>) -> Result<Option<Node>> {
        self.run(node_ops::get(node_id))
    }

    pub fn all_nodes(&mut self) -> Result<Vec<Node>> { self.run(node_ops::all()) }

    /// Import a node data sent from a child or parent node.
    pub fn import_node(&self, node: &Node) -> Result<Option<(Node, ChangelogEntry)>> {
        let local_node_id = self.require_local_node()?.node_id;
        self.transaction(|ctx: &mut Context<'_, WritableConn>| {
            if let Some(node) = node_ops::import(node).run(ctx)? {
                let change = Change::ImportNode(node);
                let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
                let Change::ImportNode(node) = change else { panic!() };
                Ok(Some((node, changelog)))
            } else {
                Ok(None)
            }
        })
    }

    /////////////////////////////////////////////////////////////////////////////
    // node owner
    /////////////////////////////////////////////////////////////////////////////

    pub fn start_owner_session(&self, password: &str, duration: Duration) -> Result<LocalNode> {
        let mut local_node = self.require_local_node()?;
        let duration = chrono::Duration::from_std(duration)?;
        local_node
            .start_session(password, duration)
            .context(DatabaseError::AuthenticationFailed)?;
        self.transaction(local_node_ops::update(&local_node))?;
        Ok(local_node.clone())
    }

    pub fn clear_owner_session(&self) -> Result<()> {
        let mut local_node = self.require_local_node()?;
        local_node.clear_session();
        self.transaction(local_node_ops::update(&local_node))?;
        Ok(())
    }

    pub fn change_owner_password(&self, password: &str) -> Result<()> {
        let mut local_node = self.require_local_node()?;
        local_node.update_password(password)?;
        self.transaction(local_node_ops::update(&local_node))?;
        Ok(())
    }

    /////////////////////////////////////////////////////////////////////////////
    // parent nodes
    /////////////////////////////////////////////////////////////////////////////

    pub fn all_parent_nodes(&mut self, operator: &Operator) -> Result<Vec<(ParentNode, Node)>> {
        operator.requires_to_be_owner(EntityKind::ParentNode, OpKind::Read)?;
        self.run(parent_node_ops::all_pairs())
    }

    /// Insert or update a parent node. It is an idempotent operation.
    ///
    /// The node has to be imported before registered as a parent.
    pub fn put_parent_node(
        &self,
        id: &Id<Node>,
        url_prefix: &str,
        operator: &Operator,
    ) -> Result<ParentNode> {
        let mut globals = (self.get_globals)();
        if let Some(parent_node) = globals.parent_nodes.get_mut(id) {
            operator.requires_to_be_owner(EntityKind::ParentNode, OpKind::Update)?;
            parent_node.url_prefix = url_prefix.into();
            self.transaction(parent_node_ops::update(&parent_node))
        } else {
            operator.requires_to_be_owner(EntityKind::ParentNode, OpKind::Create)?;
            let new_parent_node = NewParentNode::new(id, url_prefix)?;
            self.transaction(|ctx: &mut Context<'_, WritableConn>| {
                let parent_node = parent_node_ops::insert(&new_parent_node).run(ctx)?;
                globals.parent_nodes.insert(*id, parent_node.clone());
                Ok(parent_node)
            })
        }
    }

    pub fn parent_node(&mut self, id: &Id<Node>) -> Option<ParentNode> {
        (self.get_globals)().parent_nodes.get(id).map(|n| n.clone())
    }

    pub fn save_parent_node_password(
        &self,
        id: &Id<Node>,
        password: &str,
        encryption_password: &str,
        operator: &Operator,
    ) -> Result<ParentNode> {
        operator.requires_to_be_owner(EntityKind::ParentNode, OpKind::Update)?;
        let mut parent_node = self.require_parent_node(id)?;
        parent_node.save_password(password, encryption_password)?;
        self.transaction(parent_node_ops::update(&parent_node))
    }

    /////////////////////////////////////////////////////////////////////////////
    // child nodes
    /////////////////////////////////////////////////////////////////////////////

    pub fn all_child_nodes(&mut self, operator: &Operator) -> Result<Vec<(ChildNode, Node)>> {
        operator.requires_to_be_owner(EntityKind::ChildNode, OpKind::Read)?;
        self.run(child_node_ops::all_pairs())
    }

    pub fn recent_child_nodes(
        &mut self,
        page_size: i64,
        page_index: i64,
        operator: &Operator,
    ) -> Result<Paginated<(ChildNode, Node)>> {
        operator.requires_to_be_owner(EntityKind::ChildNode, OpKind::Read)?;
        self.run(child_node_ops::recent_pairs(page_size, page_index))
    }

    /// Add a child node by its ID.
    ///
    /// This operation is assumed to be invoked by a node owner to allow another node
    /// to connect to this node.
    ///
    /// If the node specified by the ID doesn't exist in this database,
    /// this function will create a placeholder row in the `nodes` table to be
    /// replace with real data, which will be sent from the node when logging in later.
    pub fn add_child_node(
        &self,
        id: Id<Node>,
        password: &str,
        as_owner: bool,
        can_edit_links: bool,
        operator: &Operator,
    ) -> Result<ChildNode> {
        operator.requires_to_be_owner(EntityKind::ChildNode, OpKind::Create)?;
        self.transaction(|ctx: &mut Context<'_, WritableConn>| {
            let node = node_ops::get_or_insert_placeholder(id).run(ctx)?;
            let new_child = NewChildNode::new(&node.uuid, password, as_owner, can_edit_links)?;
            child_node_ops::insert(&new_child).run(ctx)
        })
    }

    pub fn start_child_session(
        &self,
        id: &Id<Node>,
        password: &str,
        duration: Duration,
    ) -> Result<ChildNode> {
        let duration = chrono::Duration::from_std(duration)?;
        self.transaction(|ctx: &mut Context<'_, WritableConn>| {
            let mut child_node = child_node_ops::get_or_err(id)
                .run(ctx)?
                // Hide a not-found error for a security reason
                .context(DatabaseError::AuthenticationFailed)?;
            child_node
                .start_session(password, duration)
                .context(DatabaseError::AuthenticationFailed)?;
            child_node = child_node_ops::update(&child_node).run(ctx)?;
            Ok(child_node)
        })
    }

    pub fn clear_child_session(&self, id: &Id<Node>) -> Result<()> {
        self.transaction(|ctx: &mut Context<'_, WritableConn>| {
            let mut child_node = child_node_ops::get_or_err(id).run(ctx)??;
            child_node.clear_session();
            child_node_ops::update(&child_node).run(ctx)?;
            Ok(())
        })
    }

    pub fn change_child_password(&self, id: &Id<Node>, password: &str) -> Result<()> {
        self.transaction(|ctx: &mut Context<'_, WritableConn>| {
            let mut child_node = child_node_ops::get_or_err(id).run(ctx)??;
            child_node.update_password(password)?;
            child_node_ops::update(&child_node).run(ctx)?;
            Ok(())
        })
    }

    /////////////////////////////////////////////////////////////////////////////
    // operator
    /////////////////////////////////////////////////////////////////////////////

    pub fn local_node_as_operator(&self) -> Result<Operator> {
        let local_node_id = self.require_local_node()?.node_id;
        Ok(Operator::Owner(local_node_id))
    }

    pub fn operator_in_session(&mut self, token: &str) -> Result<Option<Operator>> {
        // one of child nodes?
        if let Some(child_node) = self.run(child_node_ops::get_by_session_token(token))? {
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

    pub fn import_change(
        &self,
        log: &ChangelogEntry,
        parent_node_id: &Id<Node>,
    ) -> Result<Option<ChangelogEntry>> {
        let mut parent_node = self.require_parent_node(parent_node_id)?;
        self.transaction(changelog_ops::import_change(log, &mut parent_node))
    }

    pub fn chunk_of_changes(
        &mut self,
        from: i64,
        limit: i64,
    ) -> Result<(Vec<ChangelogEntry>, i64)> {
        self.run(changelog_ops::chunk(from, limit))
    }

    /////////////////////////////////////////////////////////////////////////////
    // cotos
    /////////////////////////////////////////////////////////////////////////////

    pub fn coto(&mut self, id: &Id<Coto>) -> Result<Option<Coto>> { self.run(coto_ops::get(id)) }

    pub fn all_cotos(&mut self) -> Result<Vec<Coto>> { self.run(coto_ops::all()) }

    pub fn recent_cotos(
        &mut self,
        node_id: Option<&Id<Node>>,
        posted_in_id: Option<&Id<Cotonoma>>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Paginated<Coto>> {
        self.run(coto_ops::recent(
            node_id,
            posted_in_id,
            page_size,
            page_index,
        ))
    }

    /// Posts a coto in the specified cotonoma.
    ///
    /// The target cotonoma has to belong to the local node,
    /// otherwise a change should be made via [Self::import_change()].
    pub fn post_coto(
        &self,
        content: &str,
        summary: Option<&str>,
        posted_in: &Cotonoma,
        operator: &Operator,
    ) -> Result<(Coto, ChangelogEntry)> {
        self.ensure_local(posted_in)?;

        let local_node_id = self.require_local_node()?.node_id;
        let posted_by_id = operator.node_id();
        let new_coto = NewCoto::new(
            &local_node_id,
            &posted_in.uuid,
            &posted_by_id,
            content,
            summary,
        )?;
        self.transaction(|ctx: &mut Context<'_, WritableConn>| {
            let inserted_coto = coto_ops::insert(&new_coto).run(ctx)?;
            let change = Change::CreateCoto(inserted_coto.clone());
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((inserted_coto, changelog))
        })
    }

    pub fn edit_coto(
        &self,
        id: &Id<Coto>,
        content: &str,
        summary: Option<&str>,
        operator: &Operator,
    ) -> Result<(Coto, ChangelogEntry)> {
        let local_node_id = self.require_local_node()?.node_id;
        self.transaction(|ctx: &mut Context<'_, WritableConn>| {
            let coto = coto_ops::get_or_err(id).run(ctx)??;
            self.ensure_local(&coto)?;
            operator.can_update_coto(&coto)?;
            let coto = coto_ops::update(&coto.edit(content, summary)).run(ctx)?;
            let change = Change::EditCoto {
                uuid: *id,
                content: content.into(),
                summary: summary.map(String::from),
                updated_at: coto.updated_at,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((coto, changelog))
        })
    }

    pub fn delete_coto(&self, id: &Id<Coto>, operator: &Operator) -> Result<ChangelogEntry> {
        let local_node_id = self.require_local_node()?.node_id;
        self.transaction(|ctx: &mut Context<'_, WritableConn>| {
            let coto = coto_ops::get_or_err(id).run(ctx)??;
            self.ensure_local(&coto)?;
            operator.can_delete_coto(&coto)?;
            if coto_ops::delete(id).run(ctx)? {
                let change = Change::DeleteCoto(*id);
                let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
                Ok(changelog)
            } else {
                Err(DatabaseError::not_found(EntityKind::Coto, *id))?
            }
        })
    }

    /////////////////////////////////////////////////////////////////////////////
    // cotonomas
    /////////////////////////////////////////////////////////////////////////////

    pub fn root_cotonoma(&mut self) -> Result<Option<(Cotonoma, Coto)>> {
        if let Some(id) = (self.get_globals)().root_cotonoma_id {
            self.cotonoma(&id)
        } else {
            Ok(None)
        }
    }

    pub fn cotonoma(&mut self, id: &Id<Cotonoma>) -> Result<Option<(Cotonoma, Coto)>> {
        op::run(&mut self.ro_conn, cotonoma_ops::get(id))
    }

    pub fn cotonoma_or_err(&mut self, id: &Id<Cotonoma>) -> Result<(Cotonoma, Coto)> {
        let cotonoma = self.run(cotonoma_ops::get_or_err(id))??;
        Ok(cotonoma)
    }

    pub fn all_cotonomas(&mut self) -> Result<Vec<Cotonoma>> { self.run(cotonoma_ops::all()) }

    pub fn recent_cotonomas(
        &mut self,
        node_id: Option<&Id<Node>>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Paginated<Cotonoma>> {
        self.run(cotonoma_ops::recent(node_id, page_size, page_index))
    }

    /////////////////////////////////////////////////////////////////////////////
    // internals
    /////////////////////////////////////////////////////////////////////////////

    fn run<Op, T>(&mut self, op: Op) -> Result<T>
    where
        Op: Operation<SqliteConnection, T>,
    {
        op::run(&mut self.ro_conn, op)
    }

    fn transaction<Op, T>(&self, op: Op) -> Result<T>
    where
        Op: Operation<WritableConn, T>,
    {
        op::run_in_transaction(&mut (self.get_rw_conn)(), op)
    }

    fn require_local_node(&self) -> Result<MappedMutexGuard<LocalNode>> {
        MutexGuard::try_map((self.get_globals)(), |g| g.local_node.as_mut())
            .map_err(|_| anyhow!("Local node has not yet been created."))
    }

    fn require_parent_node(&self, id: &Id<Node>) -> Result<MappedMutexGuard<ParentNode>> {
        MutexGuard::try_map((self.get_globals)(), |g| g.parent_nodes.get_mut(id))
            .map_err(|_| anyhow!("Parent node {} was not found in the globals.", id))
    }

    fn ensure_local<T: BelongsToNode + std::fmt::Debug>(&self, entity: &T) -> Result<()> {
        let local_node_id = self.require_local_node()?.node_id;
        if *entity.node_id() != local_node_id {
            bail!("The entity doesn't belong to the local node: {:?}", entity);
        }
        Ok(())
    }
}
