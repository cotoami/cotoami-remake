//! A cotonoma is a specific type of [Coto] in which other cotos are posted.

use anyhow::Result;
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use diesel::prelude::*;
use validator::Validate;

use super::{
    coto::Coto,
    node::{BelongsToNode, Node},
    Id,
};
use crate::schema::cotonomas;

/////////////////////////////////////////////////////////////////////////////
// Cotonoma
/////////////////////////////////////////////////////////////////////////////

/// A row in `cotonomas` table
#[derive(
    Debug,
    Clone,
    PartialEq,
    Eq,
    Identifiable,
    AsChangeset,
    Queryable,
    Selectable,
    Validate,
    serde::Serialize,
    serde::Deserialize,
)]
#[diesel(primary_key(uuid))]
pub struct Cotonoma {
    /// Universally unique cotonoma ID
    pub uuid: Id<Cotonoma>,

    /// UUID of the node in which this cotonoma was created
    pub node_id: Id<Node>,

    /// Coto UUID of this cotonoma
    pub coto_id: Id<Coto>,

    /// Name of this cotonoma
    #[validate(length(max = "Cotonoma::NAME_MAX_LENGTH"))]
    pub name: String,

    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,

    /// The number of posts in this cotonoma
    pub number_of_posts: i64,

    /// The number of links in this cotonoma
    pub number_of_links: i64,
}

impl Cotonoma {
    pub const NAME_MAX_LENGTH: usize = 50;

    pub fn created_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.created_at) }

    pub fn updated_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.updated_at) }

    pub fn to_update(&self) -> UpdateCotonoma {
        UpdateCotonoma {
            uuid: &self.uuid,
            name: &self.name,
            updated_at: crate::current_datetime(),
        }
    }

    pub fn to_import(&self) -> NewCotonoma {
        NewCotonoma {
            uuid: self.uuid,
            node_id: &self.node_id,
            coto_id: &self.coto_id,
            name: &self.name,
            created_at: self.created_at,
            updated_at: self.updated_at,
        }
    }
}

impl BelongsToNode for Cotonoma {
    fn node_id(&self) -> &Id<Node> { &self.node_id }
}

/////////////////////////////////////////////////////////////////////////////
// NewCotonoma
/////////////////////////////////////////////////////////////////////////////

/// An `Insertable` cotonoma data
#[derive(Insertable, Validate)]
#[diesel(table_name = cotonomas)]
pub struct NewCotonoma<'a> {
    uuid: Id<Cotonoma>,
    node_id: &'a Id<Node>,
    coto_id: &'a Id<Coto>,
    #[validate(length(max = "Cotonoma::NAME_MAX_LENGTH"))]
    name: &'a str,
    created_at: NaiveDateTime,
    updated_at: NaiveDateTime,
}

impl<'a> NewCotonoma<'a> {
    pub fn new(node_id: &'a Id<Node>, coto_id: &'a Id<Coto>, name: &'a str) -> Result<Self> {
        let now = crate::current_datetime();
        let cotonoma = Self {
            uuid: Id::generate(),
            node_id,
            coto_id,
            name,
            created_at: now,
            updated_at: now,
        };
        cotonoma.validate()?;
        Ok(cotonoma)
    }
}

/////////////////////////////////////////////////////////////////////////////
// UpdateCotonoma
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Identifiable, AsChangeset, Validate)]
#[diesel(table_name = cotonomas, primary_key(uuid))]
pub struct UpdateCotonoma<'a> {
    uuid: &'a Id<Cotonoma>,
    #[validate(length(max = "Cotonoma::NAME_MAX_LENGTH"))]
    pub name: &'a str,
    pub updated_at: NaiveDateTime,
}
