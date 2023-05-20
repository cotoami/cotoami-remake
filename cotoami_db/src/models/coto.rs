//! Core concepts in a Cotoami database (Coto, Cotonoma, Link)
//!
//! - Coto
//!     - A coto is a unit of data in a cotoami database.
//! - Cotonoma
//!     - A cotonoma is a specific type of coto in which other cotos are posted.
//! - Link
//!     - A link is a directed edge connecting two cotos.

use super::node::Node;
use super::{Id, Ids};
use crate::schema::{cotonomas, cotos, links};
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use diesel::prelude::*;

/////////////////////////////////////////////////////////////////////////////
// cotos
/////////////////////////////////////////////////////////////////////////////

/// A row in `cotos` table
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

impl Coto {
    pub fn created_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.created_at)
    }

    pub fn updated_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.updated_at)
    }

    pub fn to_import(&self) -> NewCoto {
        NewCoto {
            uuid: self.uuid.clone(),
            node_id: &self.node_id,
            posted_in_id: self.posted_in_id.as_ref(),
            posted_by_id: &self.posted_by_id,
            content: self.content.as_deref(),
            summary: self.summary.as_deref(),
            is_cotonoma: self.is_cotonoma,
            repost_of_id: self.repost_of_id.as_ref(),
            reposted_in_ids: self.reposted_in_ids.as_ref(),
            created_at: Some(self.created_at),
            updated_at: Some(self.updated_at),
        }
    }
}

/// An `Insertable` coto data
#[derive(Insertable)]
#[diesel(table_name = cotos)]
pub struct NewCoto<'a> {
    uuid: Id<Coto>,
    node_id: &'a Id<Node>,
    posted_in_id: Option<&'a Id<Cotonoma>>,
    posted_by_id: &'a Id<Node>,
    content: Option<&'a str>,
    summary: Option<&'a str>,
    is_cotonoma: bool,
    repost_of_id: Option<&'a Id<Coto>>,
    reposted_in_ids: Option<&'a Ids<Cotonoma>>,
    created_at: Option<NaiveDateTime>,
    updated_at: Option<NaiveDateTime>,
}

impl<'a> NewCoto<'a> {
    fn new_base(node_id: &'a Id<Node>, posted_by_id: &'a Id<Node>) -> Self {
        let uuid = Id::generate();
        Self {
            uuid,
            node_id,
            posted_in_id: None,
            posted_by_id,
            content: None,
            summary: None,
            is_cotonoma: false,
            repost_of_id: None,
            reposted_in_ids: None,
            created_at: None,
            updated_at: None,
        }
    }

    pub fn new(
        node_id: &'a Id<Node>,
        posted_in_id: &'a Id<Cotonoma>,
        posted_by_id: &'a Id<Node>,
        content: &'a str,
        summary: Option<&'a str>,
    ) -> Self {
        let mut coto = Self::new_base(node_id, posted_by_id);
        coto.posted_in_id = Some(posted_in_id);
        coto.content = Some(content);
        coto.summary = summary;
        coto
    }

    pub fn new_cotonoma(
        node_id: &'a Id<Node>,
        posted_in_id: &'a Id<Cotonoma>,
        posted_by_id: &'a Id<Node>,
        name: &'a str,
    ) -> Self {
        let mut coto = Self::new_base(node_id, posted_by_id);
        coto.posted_in_id = Some(posted_in_id);
        coto.summary = Some(name); // a cotonoma name is stored as a summary
        coto.is_cotonoma = true;
        coto
    }

    pub fn new_root_cotonoma(node_id: &'a Id<Node>, name: &'a str) -> Self {
        let mut coto = Self::new_base(node_id, node_id);
        coto.summary = Some(name); // a cotonoma name is stored as a summary
        coto.is_cotonoma = true;
        coto
    }
}

/////////////////////////////////////////////////////////////////////////////
// cotonomas
/////////////////////////////////////////////////////////////////////////////

/// A row in `cotonomas` table
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

impl Cotonoma {
    pub fn created_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.created_at)
    }

    pub fn updated_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.updated_at)
    }

    pub fn to_import(&self) -> NewCotonoma {
        NewCotonoma {
            uuid: self.uuid.clone(),
            node_id: &self.node_id,
            coto_id: &self.coto_id,
            name: &self.name,
            created_at: Some(self.created_at),
            updated_at: Some(self.updated_at),
        }
    }
}

/// An `Insertable` cotonoma data
#[derive(Insertable)]
#[diesel(table_name = cotonomas)]
pub struct NewCotonoma<'a> {
    uuid: Id<Cotonoma>,
    node_id: &'a Id<Node>,
    coto_id: &'a Id<Coto>,
    name: &'a str,
    created_at: Option<NaiveDateTime>,
    updated_at: Option<NaiveDateTime>,
}

impl<'a> NewCotonoma<'a> {
    pub fn new(node_id: &'a Id<Node>, coto_id: &'a Id<Coto>, name: &'a str) -> Self {
        let uuid = Id::generate();
        Self {
            uuid,
            node_id,
            coto_id,
            name,
            created_at: None,
            updated_at: None,
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// links
/////////////////////////////////////////////////////////////////////////////

/// A row in `links` table
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
