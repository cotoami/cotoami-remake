use super::Id;
use crate::schema::nodes;
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use diesel::prelude::*;

/// A node is a single cotoami database that has connections to/from other databases(nodes).
///
/// Identifiable:
/// * This struct represents a single row in a database table `nodes`.
/// * It will assume that the table name is the plural `snake_case` form of this struct name.
/// * It allows you to pass this struct to `update`.
///
/// Queryable:
/// * This struct represents the result of a SQL query.
/// * It assumes that the order of fields on this struct matches the columns of `nodes` in `schema.rs`.
#[derive(
    Debug, Clone, Eq, PartialEq, Identifiable, Queryable, serde::Serialize, serde::Deserialize,
)]
#[diesel(primary_key(rowid))]
pub struct Node {
    /// SQLite rowid (so-called "integer primary key")
    #[serde(skip_serializing, skip_deserializing)]
    pub rowid: i64,

    /// Globally unique node ID
    pub uuid: Id<Node>,

    /// Display name
    pub name: Option<String>,

    /// Icon image
    pub icon: Option<Vec<u8>>,

    /// For nodes being connected from this node
    pub url_prefix: Option<String>,

    /// For authenticating nodes connecting to this node
    pub password: Option<String>,

    /// Permission to edit links in the database of this node.
    pub can_edit_links: bool,

    /// Version of node info for synchronizing among databases
    pub version: i32,

    /// Creation date of this node
    pub created_at: NaiveDateTime,

    /// Registration date in this database
    pub inserted_at: NaiveDateTime,
}

impl Node {
    pub fn created_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.created_at)
    }

    pub fn inserted_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.inserted_at)
    }
}
