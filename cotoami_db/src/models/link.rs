//! A link is a directed edge connecting two [Coto]s.

use anyhow::Result;
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use diesel::prelude::*;
use validator::Validate;

use super::{
    coto::Coto,
    cotonoma::Cotonoma,
    node::{BelongsToNode, Node},
    Id,
};
use crate::schema::links;

/////////////////////////////////////////////////////////////////////////////
// Link
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
    Validate,
    serde::Serialize,
    serde::Deserialize,
)]
#[diesel(primary_key(uuid))]
pub struct Link {
    /// Universally unique link ID
    pub uuid: Id<Link>,

    /// UUID of the node in which this link was created
    pub node_id: Id<Node>,

    /// UUID of the cotonoma in which this link was created,
    /// or `None` if it does not belong to a cotonoma.
    pub created_in_id: Option<Id<Cotonoma>>,

    /// UUID of the node whose owner has created this link
    pub created_by_id: Id<Node>,

    /// UUID of the coto at the source of this link
    pub source_coto_id: Id<Coto>,

    /// UUID of the coto at the target of this link
    pub target_coto_id: Id<Coto>,

    /// Linkng phrase to express the relationship between the two cotos
    #[validate(length(max = "Link::LINKING_PHRASE_MAX_LENGTH"))]
    pub linking_phrase: Option<String>,

    /// Content attached to this link
    pub details: Option<String>,

    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
}

impl Link {
    pub const LINKING_PHRASE_MAX_LENGTH: usize = 200;

    pub fn created_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.created_at) }

    pub fn updated_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.updated_at) }

    pub fn to_update(&self) -> UpdateLink {
        UpdateLink {
            uuid: &self.uuid,
            linking_phrase: self.linking_phrase.as_deref(),
            updated_at: crate::current_datetime(),
        }
    }

    pub fn to_import(&self) -> NewLink {
        NewLink {
            uuid: self.uuid,
            node_id: &self.node_id,
            created_by_id: &self.created_by_id,
            source_coto_id: &self.source_coto_id,
            target_coto_id: &self.target_coto_id,
            linking_phrase: self.linking_phrase.as_deref(),
            created_at: self.created_at,
            updated_at: self.updated_at,
        }
    }
}

impl BelongsToNode for Link {
    fn node_id(&self) -> &Id<Node> { &self.node_id }
}

/////////////////////////////////////////////////////////////////////////////
// NewLink
/////////////////////////////////////////////////////////////////////////////

/// An `Insertable` link data
#[derive(Insertable, Validate)]
#[diesel(table_name = links)]
pub struct NewLink<'a> {
    uuid: Id<Link>,
    node_id: &'a Id<Node>,
    created_by_id: &'a Id<Node>,
    source_coto_id: &'a Id<Coto>,
    target_coto_id: &'a Id<Coto>,
    #[validate(length(max = "Link::LINKING_PHRASE_MAX_LENGTH"))]
    linking_phrase: Option<&'a str>,
    created_at: NaiveDateTime,
    updated_at: NaiveDateTime,
}

impl<'a> NewLink<'a> {
    pub fn new(
        node_id: &'a Id<Node>,
        created_by_id: &'a Id<Node>,
        source_coto_id: &'a Id<Coto>,
        target_coto_id: &'a Id<Coto>,
        linking_phrase: Option<&'a str>,
    ) -> Result<Self> {
        let now = crate::current_datetime();
        let link = Self {
            uuid: Id::generate(),
            node_id,
            created_by_id,
            source_coto_id,
            target_coto_id,
            linking_phrase,
            created_at: now,
            updated_at: now,
        };
        link.validate()?;
        Ok(link)
    }
}

/////////////////////////////////////////////////////////////////////////////
// UpdateLink
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Identifiable, AsChangeset, Validate)]
#[diesel(table_name = links, primary_key(uuid))]
pub struct UpdateLink<'a> {
    uuid: &'a Id<Link>,
    #[validate(length(max = "Link::LINKING_PHRASE_MAX_LENGTH"))]
    pub linking_phrase: Option<&'a str>,
    pub updated_at: NaiveDateTime,
}
