//! Database operations and transactions

use anyhow::Result;
use diesel::sqlite::SqliteConnection;
use diesel::Connection;
use diesel_migrations::{embed_migrations, EmbeddedMigrations, MigrationHarness};
use error::DatabaseError;
use op::WritableConnection;
use parking_lot::{Mutex, MutexGuard};
use std::path::{Path, PathBuf};
use std::time::Duration;
use url::Url;

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
    uri: String,

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

    pub fn create_session<'a>(&'a self) -> Result<DatabaseSession<'a>> {
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
        let mut conn = SqliteConnection::establish(&self.uri)?;
        conn.run_pending_migrations(Self::MIGRATIONS).unwrap();
        Ok(())
    }

    fn get_rw_conn<'a>(&'a self) -> MutexGuard<'a, WritableConnection> {
        self.rw_conn.lock()
    }

    fn get_ro_conn(&self) -> Result<SqliteConnection> {
        let database_uri = format!("{}?mode=ro&_txlock=deferred", &self.uri);
        let ro_conn = SqliteConnection::establish(&database_uri)?;
        Ok(ro_conn)
    }
}

pub struct DatabaseSession<'a> {
    get_rw_conn: Box<dyn Fn() -> MutexGuard<'a, WritableConnection> + 'a>,
    ro_conn: SqliteConnection,
}

impl<'a> DatabaseSession<'a> {}
