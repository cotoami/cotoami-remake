//! Cotoami database based on [SQLite](https://sqlite.org/) and [Diesel](https://diesel.rs/)

use chrono::offset::Utc;
use chrono::NaiveDateTime;

pub mod db;
pub mod models;
pub mod schema;

pub fn current_datetime() -> NaiveDateTime {
    Utc::now().naive_utc()
}
