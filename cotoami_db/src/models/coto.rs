//! Core concepts in a Cotoami database (Coto, Cotonoma, Link)

use super::node::Node;
use super::{Id, Ids};
use crate::schema::{cotonomas, cotos, links};
use chrono::NaiveDateTime;
use diesel::prelude::*;

/// A coto is a unit of data in a cotoami database.
#[derive(
    Debug,
    Clone,
    Eq,
    PartialEq,
    Identifiable,
    AsChangeset,
    Queryable,
    serde::Serialize,
    serde::Deserialize,
)]
#[diesel(primary_key(rowid))]
pub struct Coto {
    /// SQLite rowid (so-called "integer primary key")
    #[serde(skip_serializing, skip_deserializing)]
    pub rowid: i64,

    /// Universally unique coto ID
    pub uuid: Id<Coto>,

    /// UUID of the node in which this coto was created
    pub node_id: Id<Node>,

    /// UUID of the cotonoma in which this coto was posted
    ///
    /// `None` if it is the root cotonoma.
    pub posted_in_id: Option<Id<Cotonoma>>,

    /// UUID of the node whose owner has posted this coto
    pub posted_by_id: Id<Node>,

    /// Content of this coto
    ///
    /// `None` if it is a repost.
    pub content: Option<String>,

    /// Optional summary of the content for compact display
    pub summary: Option<String>,

    /// TRUE if this coto is a cotonoma
    pub is_cotonoma: bool,

    /// UUID of the original coto of this repost
    ///
    /// `None` if it is not a repost.
    pub repost_of_id: Option<Id<Coto>>,

    /// UUIDs of the cotonomas in which this coto was reposted
    pub reposted_in_ids: Option<Ids<Cotonoma>>,

    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
}

/// A cotonoma is a specific type of coto in which other cotos are posted.
#[derive(
    Debug,
    Clone,
    Eq,
    PartialEq,
    Identifiable,
    AsChangeset,
    Queryable,
    serde::Serialize,
    serde::Deserialize,
)]
#[diesel(primary_key(rowid))]
pub struct Cotonoma {
    /// SQLite rowid (so-called "integer primary key")
    #[serde(skip_serializing, skip_deserializing)]
    pub rowid: i64,

    /// Universally unique cotonoma ID
    pub uuid: Id<Cotonoma>,

    /// UUID of the node in which this cotonoma was created
    pub node_id: Id<Node>,

    /// Coto UUID of this cotonoma
    pub coto_id: Id<Coto>,

    /// Name of this cotonoma
    pub name: String,

    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
}

/// A link is a directed edge connecting two cotos.
#[derive(
    Debug,
    Clone,
    Eq,
    PartialEq,
    Identifiable,
    AsChangeset,
    Queryable,
    serde::Serialize,
    serde::Deserialize,
)]
#[diesel(primary_key(rowid))]
pub struct Link {
    /// SQLite rowid (so-called "integer primary key")
    #[serde(skip_serializing, skip_deserializing)]
    pub rowid: i64,

    /// Universally unique link ID
    pub uuid: Id<Link>,

    /// UUID of the node in which this link was created
    pub node_id: Id<Node>,

    /// UUID of the node whose owner has created this link
    pub created_by_id: Id<Node>,

    /// UUID of the coto at the tail of this link
    pub tail_coto_id: Id<Coto>,

    /// UUID of the coto at the head of this link
    pub head_coto_id: Id<Coto>,

    /// Linkng phrase to express the relationship between the two cotos
    pub linking_phrase: Option<String>,

    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
}
