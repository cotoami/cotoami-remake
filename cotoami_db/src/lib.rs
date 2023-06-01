//! Cotoami database based on [SQLite](https://sqlite.org/) and [Diesel](https://diesel.rs/)

use chrono::offset::Utc;
use chrono::NaiveDateTime;

pub mod db;
pub mod models;
pub mod schema;

/// Returns the current datetime in UTC.
/// It is to recreate SQLite's `CURRENT_TIMESTAMP` in the Rust side.
pub fn current_datetime() -> NaiveDateTime {
    Utc::now().naive_utc()
}
