//! Cotoami database based on [SQLite](https://sqlite.org/) and [Diesel](https://diesel.rs/)

pub use db::{Database, DatabaseSession};
pub use models::changelog::{Change, ChangelogEntry};
pub use models::coto::{Coto, Cotonoma};
pub use models::node::Node;
pub use models::Id;

use chrono::offset::Utc;
use chrono::NaiveDateTime;

pub mod db;
pub mod models;
mod schema;

/// Returns the current datetime in UTC.
fn current_datetime() -> NaiveDateTime {
    Utc::now().naive_utc()
}
