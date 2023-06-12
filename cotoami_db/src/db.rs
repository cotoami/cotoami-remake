//! Database operations and transactions

use crate::models::changelog::{Change, ChangelogEntry};
use crate::models::coto::{Coto, Cotonoma, NewCoto};
use crate::models::node::Node;
use crate::models::Id;
use anyhow::{anyhow, Result};
use diesel::sqlite::SqliteConnection;
use diesel::Connection;
use diesel_migrations::{embed_migrations, EmbeddedMigrations, MigrationHarness};
use error::DatabaseError;
use log::info;
use op::{composite_op, Operation, WritableConnection};
use ops::*;
use parking_lot::{Mutex, MutexGuard};
use std::path::{Path, PathBuf};
use std::time::Duration;
use url::Url;

use self::ops::cotonoma_ops;

pub mod error;
pub mod op;
pub mod ops;
pub mod sqlite;

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
    rw_conn: Mutex<WritableConnection>,

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

        let node = db.create_session()?.as_node()?;
        db.globals.lock().node_id = node.map(|n| n.uuid);

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

    pub fn create_rw_conn(uri: &str) -> Result<WritableConnection> {
        let mut rw_conn = SqliteConnection::establish(uri)?;
        sqlite::enable_foreign_key_constraints(&mut rw_conn)?;
        sqlite::enable_wal(&mut rw_conn)?;
        sqlite::set_busy_timeout(&mut rw_conn, Self::SQLITE_BUSY_TIMEOUT)?;
        Ok(WritableConnection::new(rw_conn))
    }

    pub fn to_file_uri<P: AsRef<Path>>(path: P) -> Result<String> {
        let path = path.as_ref();
        if path.is_dir() {
            Err(DatabaseError::InvalidFilePath {
                path: path.to_path_buf(),
                reason: "The path is a directory".to_owned(),
            })?
        } else {
            // Url::from_file_path
            // This returns Err if the given path is not absolute or, on Windows,
            // if the prefix is not a disk prefix (e.g. C:) or a UNC prefix (\\).
            // https://docs.rs/url/2.2.2/url/struct.Url.html#method.from_file_path
            let uri = Url::from_file_path(path).or(Err(DatabaseError::InvalidFilePath {
                path: path.to_path_buf(),
                reason: "Invalid path".to_owned(),
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

/// Global information shared among sessions in a database
#[derive(Debug, Default)]
struct Globals {
    /// ID of this node
    node_id: Option<Id<Node>>,
}

pub struct DatabaseSession<'a> {
    ro_conn: SqliteConnection,
    get_rw_conn: Box<dyn Fn() -> MutexGuard<'a, WritableConnection> + 'a>,
    get_globals: Box<dyn Fn() -> MutexGuard<'a, Globals> + 'a>,
}

impl<'a> DatabaseSession<'a> {
    /////////////////////////////////////////////////////////////////////////////
    // nodes
    /////////////////////////////////////////////////////////////////////////////

    pub fn as_node(&mut self) -> Result<Option<Node>> {
        op::run(&mut self.ro_conn, node_ops::get_self())
    }

    pub fn init_as_empty_node(&mut self, password: Option<&str>) -> Result<Node> {
        let op = node_ops::create_self("", password);
        op::run_in_transaction(&mut (self.get_rw_conn)(), op).map(|node| {
            (self.get_globals)().node_id = Some(node.uuid);
            node
        })
    }

    pub fn init_as_node<'b>(
        &mut self,
        name: &'b str,
        password: Option<&'b str>,
    ) -> Result<(Node, ChangelogEntry)> {
        let op = composite_op::<WritableConnection, _, _>(move |ctx| {
            let node = node_ops::create_self(name, password).run(ctx)?;

            let (cotonoma, coto) = cotonoma_ops::create_root(&node.uuid, name).run(ctx)?;

            let mut update_node = node.to_update();
            update_node.root_cotonoma_id = Some(&cotonoma.uuid);
            let node = node_ops::update(&update_node).run(ctx)?;

            let change = Change::CreateCotonoma(cotonoma, coto);
            let changelog = changelog_ops::log_change(&change).run(ctx)?;

            Ok((node, changelog))
        });
        op::run_in_transaction(&mut (self.get_rw_conn)(), op).map(|(node, changelog)| {
            (self.get_globals)().node_id = Some(node.uuid);
            (node, changelog)
        })
    }

    pub fn all_nodes(&mut self) -> Result<Vec<Node>> {
        op::run(&mut self.ro_conn, node_ops::all())
    }

    pub fn get_node(&mut self, node_id: &Id<Node>) -> Result<Option<Node>> {
        op::run(&mut self.ro_conn, node_ops::get(node_id))
    }

    /////////////////////////////////////////////////////////////////////////////
    // changelog
    /////////////////////////////////////////////////////////////////////////////

    pub fn import_change<'b>(
        &self,
        parent_node_id: &'b Id<Node>,
        log: &'b ChangelogEntry,
    ) -> Result<ChangelogEntry> {
        let op = changelog_ops::import_change(parent_node_id, log);
        op::run_in_transaction(&mut (self.get_rw_conn)(), op)
    }

    /////////////////////////////////////////////////////////////////////////////
    // cotos
    /////////////////////////////////////////////////////////////////////////////

    /// Posts a coto in the specified cotonoma (`posted_in_id`).
    ///
    /// The target cotonoma has to belong to this node,
    /// otherwise a change should be made via [Self::import_change()].
    pub fn post_coto<'b>(
        &mut self,
        posted_in_id: &'b Id<Cotonoma>,
        posted_by_id: Option<&'b Id<Node>>,
        content: &'b str,
        summary: Option<&'b str>,
    ) -> Result<(Coto, ChangelogEntry)> {
        let node_id = self.self_node_id()?;
        self.ensure_cotonoma_belongs_to_node(posted_in_id, &node_id)?;
        let posted_by_id = posted_by_id.unwrap_or(&node_id);
        let new_coto = NewCoto::new(&node_id, posted_in_id, posted_by_id, content, summary)?;
        let op = composite_op::<WritableConnection, _, _>(move |ctx| {
            let inserted_coto = coto_ops::insert(&new_coto).run(ctx)?;
            let change = Change::CreateCoto(inserted_coto.clone());
            let changelog = changelog_ops::log_change(&change).run(ctx)?;
            Ok((inserted_coto, changelog))
        });
        op::run_in_transaction(&mut (self.get_rw_conn)(), op)
    }

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

    /////////////////////////////////////////////////////////////////////////////
    // cotonomas
    /////////////////////////////////////////////////////////////////////////////

    pub fn get_cotonoma(&mut self, cotonoma_id: &Id<Cotonoma>) -> Result<Option<(Cotonoma, Coto)>> {
        op::run(&mut self.ro_conn, cotonoma_ops::get(cotonoma_id))
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

    fn self_node_id(&self) -> Result<Id<Node>> {
        (self.get_globals)()
            .node_id
            .ok_or(anyhow!("Self node row (rowid=1) has not yet been created."))
    }

    fn ensure_cotonoma_belongs_to_node<'b>(
        &mut self,
        cotonoma_id: &'b Id<Cotonoma>,
        node_id: &'b Id<Node>,
    ) -> Result<()> {
        let (cotonoma, _coto) = self
            .get_cotonoma(cotonoma_id)?
            .ok_or(anyhow!("Cotonoma `{:?}` not found", cotonoma_id))?;
        if cotonoma.node_id != *node_id {
            return Err(anyhow!(
                "Cotonoma `{:?}` does not belong to Node `{:?}`",
                cotonoma.name,
                node_id,
            ));
        }
        Ok(())
    }
}
