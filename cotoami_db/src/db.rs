//! Database operations and transactions

use core::time::Duration;
use std::{
    collections::HashMap,
    path::{Path, PathBuf},
};

use anyhow::{anyhow, bail, Result};
use diesel::{sqlite::SqliteConnection, Connection};
use diesel_migrations::{embed_migrations, EmbeddedMigrations, MigrationHarness};
use parking_lot::{
    MappedRwLockReadGuard, MappedRwLockWriteGuard, Mutex, RwLock, RwLockReadGuard, RwLockWriteGuard,
};
use tracing::info;
use url::Url;

use self::{error::*, op::WritableConn, ops::prelude::*, session::DatabaseSession};
use crate::models::prelude::*;

pub mod error;
pub mod op;
pub mod ops;
pub mod session;
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
    globals: Globals,
}

impl Database {
    const DATABASE_FILE_NAME: &'static str = "cotoami.db";
    const SQLITE_BUSY_TIMEOUT: Duration = Duration::from_millis(10_000);
    const MIGRATIONS: EmbeddedMigrations = embed_migrations!("migrations");

    pub fn new<P: AsRef<Path>>(root_dir: P) -> Result<Self> {
        let root_dir = root_dir.as_ref().canonicalize()?;
        if !root_dir.is_dir() {
            bail!(DatabaseError::InvalidRootDir(root_dir));
        }

        let file_uri = Self::to_file_uri(root_dir.join(Self::DATABASE_FILE_NAME))?;
        let rw_conn = Self::new_rw_conn(&file_uri)?;

        let db = Self {
            root_dir,
            file_uri,
            rw_conn: Mutex::new(rw_conn),
            globals: Globals::default(),
        };
        db.run_migrations()?;
        db.load_globals()?;

        info!("Database launched:");
        info!("  root_dir: {}", db.root_dir.display());
        info!("  file_uri: {}", db.file_uri);
        info!("  globals: {:?}", db.globals);

        Ok(db)
    }

    pub fn is_in<P: AsRef<Path>>(dir: P) -> bool {
        let mut path = dir.as_ref().to_path_buf();
        path.push(Self::DATABASE_FILE_NAME);
        path.is_file()
    }

    pub fn new_session(&self) -> Result<DatabaseSession<'_>> {
        Ok(DatabaseSession::new(
            &self.globals,
            Box::new(|| self.new_ro_conn()),
            Box::new(|| self.rw_conn.lock()),
        ))
    }

    pub fn new_rw_conn(uri: &str) -> Result<WritableConn> {
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

    fn new_ro_conn(&self) -> Result<SqliteConnection> {
        let database_uri = format!("{}?mode=ro&_txlock=deferred", &self.file_uri);
        let ro_conn = SqliteConnection::establish(&database_uri)?;
        Ok(ro_conn)
    }

    fn load_globals(&self) -> Result<()> {
        let mut conn = self.new_ro_conn()?;

        // local_node, root_cotonoma_id
        let local_node_pair = op::run_read(&mut conn, local_ops::get_pair())?;
        if let Some((local_node, node)) = local_node_pair {
            *self.globals.local_node.write() = Some(local_node);
            *self.globals.root_cotonoma_id.write() = node.root_cotonoma_id;
        }

        // parent_nodes
        *self.globals.parent_nodes.write() = op::run_read(&mut conn, parent_ops::all())?
            .into_iter()
            .map(|x| (x.node_id, x))
            .collect::<HashMap<_, _>>();

        Ok(())
    }

    pub fn globals(&self) -> &Globals { &self.globals }
}

/////////////////////////////////////////////////////////////////////////////
// Globals
/////////////////////////////////////////////////////////////////////////////

/// Global information shared among sessions in a database.
/// Most of the fields are cached database rows or column values frequently used internally.
/// For example, [LocalNode] will be used every time when authentication is needed.
#[derive(Debug, Default)]
pub struct Globals {
    local_node: RwLock<Option<LocalNode>>,
    root_cotonoma_id: RwLock<Option<Id<Cotonoma>>>,
    parent_nodes: RwLock<HashMap<Id<Node>, ParentNode>>,
}

impl Globals {
    /////////////////////////////////////////////////////////////////////////////
    // local_node
    /////////////////////////////////////////////////////////////////////////////

    pub fn has_local_node(&self) -> bool { self.local_node.read().is_some() }

    pub fn local_node_id(&self) -> Result<Id<Node>> { Ok(self.try_read_local_node()?.node_id) }

    pub fn local_node_as_operator(&self) -> Result<Operator> {
        Ok(Operator::Owner(self.local_node_id()?))
    }

    pub fn ensure_local<T: BelongsToNode + std::fmt::Debug>(&self, entity: &T) -> Result<()> {
        let local_node_id = self.try_read_local_node()?.node_id;
        if *entity.node_id() != local_node_id {
            bail!("The entity doesn't belong to the local node: {entity:?}");
        }
        Ok(())
    }

    pub fn is_local<T: BelongsToNode + std::fmt::Debug>(&self, entity: &T) -> bool {
        self.ensure_local(entity).is_ok()
    }

    fn try_read_local_node(&self) -> Result<MappedRwLockReadGuard<LocalNode>> {
        RwLockReadGuard::try_map(self.local_node.read(), |x| x.as_ref())
            .map_err(|_| anyhow!(DatabaseError::LocalNodeNotYetInitialized))
    }

    fn try_write_local_node(&self) -> Result<MappedRwLockWriteGuard<LocalNode>> {
        RwLockWriteGuard::try_map(self.local_node.write(), |x| x.as_mut())
            .map_err(|_| anyhow!(DatabaseError::LocalNodeNotYetInitialized))
    }

    /////////////////////////////////////////////////////////////////////////////
    // root_cotonoma_id
    /////////////////////////////////////////////////////////////////////////////

    pub fn root_cotonoma_id(&self) -> Option<Id<Cotonoma>> { *self.root_cotonoma_id.read() }

    /////////////////////////////////////////////////////////////////////////////
    // parent_nodes
    /////////////////////////////////////////////////////////////////////////////

    pub fn is_parent(&self, id: &Id<Node>) -> bool { self.parent_nodes.read().contains_key(id) }

    /// Returns the parent IDs in order of recently updated.
    pub fn parent_ids_in_update_order(&self) -> Vec<Id<Node>> {
        let parent_map = self.parent_nodes.read();
        let mut parents: Vec<&ParentNode> = parent_map.values().collect();
        parents.sort_by(|a, b| b.last_change_received_at.cmp(&a.last_change_received_at));
        parents.into_iter().map(|p| p.node_id).collect()
    }

    fn cache_parent_node(&self, parent: ParentNode) {
        self.parent_nodes
            .write()
            .insert(parent.node_id, parent.clone());
    }

    fn try_write_parent_node(&self, id: &Id<Node>) -> Result<MappedRwLockWriteGuard<ParentNode>> {
        RwLockWriteGuard::try_map(self.parent_nodes.write(), |x| x.get_mut(id))
            .map_err(|_| anyhow!(DatabaseError::not_found(EntityKind::ParentNode, *id)))
    }
}
