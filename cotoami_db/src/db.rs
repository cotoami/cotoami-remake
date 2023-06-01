//! Database operations and transactions

use op::WritableConnection;
use parking_lot::Mutex;
use std::path::PathBuf;

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
