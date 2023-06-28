//! Cotoami database based on [SQLite](https://sqlite.org/) and [Diesel](https://diesel.rs/)

pub mod prelude {
    pub use super::db::{Database, DatabaseSession};
    pub use super::models::changelog::{Change, ChangelogEntry};
    pub use super::models::coto::{Coto, Cotonoma};
    pub use super::models::node::local::LocalNode;
    pub use super::models::node::Node;
    pub use super::models::Id;
}

use chrono::offset::Utc;
use chrono::NaiveDateTime;

pub mod db;
pub mod models;
mod schema;

/// Returns the current datetime in UTC.
fn current_datetime() -> NaiveDateTime {
    Utc::now().naive_utc()
}
