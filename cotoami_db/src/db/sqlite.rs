use anyhow::Result;
use diesel::prelude::*;
use diesel::sqlite::SqliteConnection;
use std::time::Duration;

// Note on in-memory databases with concurrency
//
// There are a couple of reasons to keep hold of a single read-write connection
// (Mutex<WritableConnection>) in the process.
// 1. To keep the in-memory database from being deleted.
// 2. To avoid concurrent writes that possibly cause SQLITE_BUSY (vfs=memdb doesn't support WAL-mode)
//
// https://github.com/mattn/go-sqlite3/issues/959#issuecomment-890371852

/// Returns an in-memory database URI supporting concurrent queries
/// https://github.com/mattn/go-sqlite3/issues/959#issuecomment-890371852
pub fn in_memory_database_uri(db_name: &str, read_only: bool) -> String {
    if read_only {
        format!("file:/{}?mode=ro&vfs=memdb&_txlock=deferred", db_name)
    } else {
        format!("file:/{}?mode=rw&vfs=memdb&_txlock=immediate", db_name)
    }
}

pub fn enable_foreign_key_constraints(conn: &mut SqliteConnection) -> Result<()> {
    diesel::sql_query("PRAGMA foreign_keys = ON").execute(conn)?;
    Ok(())
}

pub fn enable_wal(conn: &mut SqliteConnection) -> Result<()> {
    diesel::sql_query("PRAGMA journal_mode = wal").execute(conn)?;
    Ok(())
}

/// Setting a busy handler that sleeps for a specified amount of time when a table is locked.
/// (The default busy callback is NULL)
///
/// After at least "ms" milliseconds of sleeping,
/// the handler returns 0 which causes sqlite3_step() to return SQLITE_BUSY.
///
/// Less than or equal to zero as a duration turns off all busy handlers.
///
/// https://www.sqlite.org/c3ref/busy_timeout.html
pub fn set_busy_timeout(conn: &mut SqliteConnection, duration: Duration) -> Result<()> {
    // Prepared statement didn't work with PRAGMA for some reason.
    diesel::sql_query(&format!("PRAGMA busy_timeout = {};", duration.as_millis())).execute(conn)?;
    Ok(())
}

/// Workaround for a SQLite library with the URI filename capability disabled until
/// the new version of Diesel is released (In Diesel v1.4.8, a new connection is
/// created via sqlite3_open without any flags. cf: https://www.sqlite.org/c3ref/open.html).
pub fn enable_uri_filenames() {
    unsafe {
        libsqlite3_sys::sqlite3_config(libsqlite3_sys::SQLITE_CONFIG_URI);
    }
}

pub fn attach_database(conn: &mut SqliteConnection, db_uri: &str, db_name: &str) -> Result<()> {
    let attach_sql = format!("ATTACH DATABASE '{}' AS {}", db_uri, db_name);
    diesel::sql_query(attach_sql).execute(conn)?;
    Ok(())
}
