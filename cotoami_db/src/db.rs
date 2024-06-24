//! Database operations and transactions

use core::time::Duration;
use std::path::{Path, PathBuf};

use anyhow::{bail, Result};
use diesel::{sqlite::SqliteConnection, Connection};
use diesel_migrations::{embed_migrations, EmbeddedMigrations, MigrationHarness};
use parking_lot::Mutex;
use tracing::info;
use url::Url;

use crate::{
    db::{
        error::*, globals::Globals, op::WritableConn, ops::node_role_ops::local_ops,
        transactions::DatabaseSession,
    },
    models::node::{Node, Principal},
};

pub mod error;
pub mod globals;
pub mod op;
pub mod ops;
pub mod sqlite;
pub mod transactions;

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
    const MIGRATIONS: EmbeddedMigrations = embed_migrations!("migrations");

    pub fn new<P: AsRef<Path>>(root_dir: P) -> Result<Self> {
        let root_dir = ensure_dir(root_dir)?;
        let file_uri = to_file_uri(root_dir.join(Self::DATABASE_FILE_NAME))?;
        let rw_conn = new_rw_conn(&file_uri)?;

        let mut db = Self {
            root_dir,
            file_uri,
            rw_conn: Mutex::new(rw_conn),
            globals: Globals::default(),
        };
        db.run_migrations()?;
        db.globals.init(&mut db.new_ro_conn()?)?;

        info!("Database launched:");
        info!("  root_dir: {}", db.root_dir.display());
        info!("  file_uri: {}", db.file_uri);
        info!("  globals: {:?}", db.globals);

        Ok(db)
    }

    pub fn is_in<P: AsRef<Path>>(dir: P) -> bool { Self::try_read_node_info(dir).is_ok() }

    /// Try to read a database node info from the given directory.
    pub fn try_read_node_info<P: AsRef<Path>>(root_dir: P) -> Result<Option<(Node, bool)>> {
        let db_file = ensure_dir(root_dir)?.join(Self::DATABASE_FILE_NAME);
        let mut conn = new_ro_conn(&to_file_uri(db_file)?)?;
        if let Some((local_node, node)) = op::run_read(&mut conn, local_ops::get_pair())? {
            Ok(Some((node, local_node.password_hash().is_some())))
        } else {
            Ok(None)
        }
    }

    pub fn new_session(&self) -> Result<DatabaseSession<'_>> {
        Ok(DatabaseSession::new(
            &self.globals,
            Box::new(|| self.new_ro_conn()),
            Box::new(|| self.rw_conn.lock()),
        ))
    }

    fn run_migrations(&self) -> Result<()> {
        let mut conn = SqliteConnection::establish(&self.file_uri)?;
        conn.run_pending_migrations(Self::MIGRATIONS).unwrap();
        Ok(())
    }

    fn new_ro_conn(&self) -> Result<SqliteConnection> { new_ro_conn(&self.file_uri) }

    pub fn globals(&self) -> &Globals { &self.globals }
}

const SQLITE_BUSY_TIMEOUT: Duration = Duration::from_millis(10_000);

pub fn new_rw_conn(uri: &str) -> Result<WritableConn> {
    let mut rw_conn = SqliteConnection::establish(uri)?;
    sqlite::enable_foreign_key_constraints(&mut rw_conn)?;
    sqlite::enable_wal(&mut rw_conn)?;
    sqlite::set_busy_timeout(&mut rw_conn, SQLITE_BUSY_TIMEOUT)?;
    Ok(WritableConn::new(rw_conn))
}

pub fn new_ro_conn(uri: &str) -> Result<SqliteConnection> {
    let database_uri = format!("{}?mode=ro&_txlock=deferred", uri);
    let ro_conn = SqliteConnection::establish(&database_uri)?;
    Ok(ro_conn)
}

fn ensure_dir<P: AsRef<Path>>(path: P) -> Result<PathBuf> {
    let path = path.as_ref().canonicalize()?;
    if !path.is_dir() {
        bail!(DatabaseError::InvalidRootDir(path));
    }
    Ok(path)
}

pub fn to_file_uri<P: AsRef<Path>>(path: P) -> Result<String> {
    let path = path.as_ref();
    if path.is_dir() {
        Err(DatabaseError::InvalidFilePath {
            path: path.to_path_buf(),
            reason: "The given path is a directory".into(),
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
