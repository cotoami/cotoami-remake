//! Database operations and transactions

use crate::models::node::Node;
use anyhow::Result;
use diesel::sqlite::SqliteConnection;
use diesel::Connection;
use diesel_migrations::{embed_migrations, EmbeddedMigrations, MigrationHarness};
use error::DatabaseError;
use log::info;
use op::WritableConnection;
use parking_lot::{Mutex, MutexGuard};
use std::path::{Path, PathBuf};
use std::time::Duration;
use url::Url;

use self::ops::node_ops;

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
    /// - https://www.sqlite.org/uri.html
    /// - https://www.sqlite.org/c3ref/open.html
    file_uri: String,

    /// A SqliteConnection for both read and write operations
    ///
    /// To avoid handling possible SQLITE_BUSY on concurrent writes,
    /// it keeps hold of a single read-write connection in the process.
    rw_conn: Mutex<WritableConnection>,
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
        };
        db.run_migrations()?;

        info!("Database launched:");
        info!("  root_dir: {}", db.root_dir.display());
        info!("  file_uri: {}", db.file_uri);
        info!("  node: {:?}", db.create_session()?.as_node());

        Ok(db)
    }

    pub fn create_session(&self) -> Result<DatabaseSession<'_>> {
        Ok(DatabaseSession {
            get_rw_conn: Box::new(move || self.get_rw_conn()),
            ro_conn: self.get_ro_conn()?,
        })
    }

    fn create_rw_conn(uri: &str) -> Result<WritableConnection> {
        let mut rw_conn = SqliteConnection::establish(uri)?;
        sqlite::enable_foreign_key_constraints(&mut rw_conn)?;
        sqlite::enable_wal(&mut rw_conn)?;
        sqlite::set_busy_timeout(&mut rw_conn, Self::SQLITE_BUSY_TIMEOUT)?;
        Ok(WritableConnection(rw_conn))
    }

    fn to_file_uri<P: AsRef<Path>>(path: P) -> Result<String> {
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

    fn get_rw_conn(&self) -> MutexGuard<'_, WritableConnection> {
        self.rw_conn.lock()
    }

    fn get_ro_conn(&self) -> Result<SqliteConnection> {
        let database_uri = format!("{}?mode=ro&_txlock=deferred", &self.file_uri);
        let ro_conn = SqliteConnection::establish(&database_uri)?;
        Ok(ro_conn)
    }
}

pub struct DatabaseSession<'a> {
    get_rw_conn: Box<dyn Fn() -> MutexGuard<'a, WritableConnection> + 'a>,
    ro_conn: SqliteConnection,
}

impl<'a> DatabaseSession<'a> {
    pub fn as_node(&mut self) -> Result<Option<Node>> {
        op::run(&mut self.ro_conn, node_ops::get_self())
    }

    pub fn init_as_node(&mut self, name: &'a str, password: Option<&'a str>) -> Result<Node> {
        let op = node_ops::create_self(name, password);
        op::run_in_transaction(&mut (self.get_rw_conn)(), op)
    }
}
