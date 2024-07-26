//! A link is a directed edge connecting two [Coto]s.

use std::fmt::Display;

use anyhow::Result;
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use derive_new::new;
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
    Debug, Clone, PartialEq, Eq, Identifiable, Queryable, serde::Serialize, serde::Deserialize,
)]
#[diesel(primary_key(uuid))]
pub struct Link {
    /// Universally unique link ID.
    pub uuid: Id<Link>,

    /// UUID of the node in which this link was created.
    pub node_id: Id<Node>,

    /// UUID of the cotonoma in which this link was created,
    /// or `None` if it does not belong to a cotonoma.
    pub created_in_id: Option<Id<Cotonoma>>,

    /// UUID of the node whose owner has created this link.
    pub created_by_id: Id<Node>,

    /// UUID of the coto at the source of this link.
    pub source_coto_id: Id<Coto>,

    /// UUID of the coto at the target of this link.
    pub target_coto_id: Id<Coto>,

    /// Linkng phrase to express the relationship between the two cotos.
    pub linking_phrase: Option<String>,

    /// Content attached to this link.
    pub details: Option<String>,

    /// Order of this link among the ones from the same coto.
    pub order: i32,

    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
}

impl Link {
    pub const LINKING_PHRASE_MAX_LENGTH: usize = 200;
    pub const DETAILS_MAX_LENGTH: usize = 1_000_000;

    pub fn created_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.created_at) }

    pub fn updated_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.updated_at) }

    pub fn edit<'a>(
        &'a self,
        linking_phrase: Option<&'a str>,
        details: Option<&'a str>,
    ) -> UpdateLink<'a> {
        let mut update_link = self.to_update();
        update_link.linking_phrase = Some(crate::blank_to_none(linking_phrase));
        update_link.details = Some(crate::blank_to_none(details));
        update_link
    }

    pub fn to_update(&self) -> UpdateLink<'_> { UpdateLink::new(&self.uuid) }

    pub fn to_import(&self) -> NewLink {
        NewLink {
            uuid: self.uuid,
            node_id: &self.node_id,
            created_in_id: self.created_in_id.as_ref(),
            created_by_id: &self.created_by_id,
            source_coto_id: &self.source_coto_id,
            target_coto_id: &self.target_coto_id,
            linking_phrase: self.linking_phrase.as_deref(),
            details: self.details.as_deref(),
            order: Some(self.order),
            created_at: self.created_at,
            updated_at: self.updated_at,
        }
    }
}

impl Display for Link {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let linking_phrase = self.linking_phrase.as_deref().unwrap_or_default();
        match self.details.as_deref() {
            Some(details) => write!(
                f,
                "{linking_phrase} ({})",
                crate::abbreviate_str(details, 5, "…")
                    .as_deref()
                    .unwrap_or(details)
            ),
            None => write!(f, "{linking_phrase}"),
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
    created_in_id: Option<&'a Id<Cotonoma>>,
    created_by_id: &'a Id<Node>,
    source_coto_id: &'a Id<Coto>,
    target_coto_id: &'a Id<Coto>,
    #[validate(length(max = "Link::LINKING_PHRASE_MAX_LENGTH"))]
    linking_phrase: Option<&'a str>,
    #[validate(length(max = "Link::DETAILS_MAX_LENGTH"))]
    details: Option<&'a str>,
    #[validate(range(min = 1))]
    pub order: Option<i32>,
    created_at: NaiveDateTime,
    updated_at: NaiveDateTime,
}

impl<'a> NewLink<'a> {
    pub fn new(
        node_id: &'a Id<Node>,
        created_in_id: Option<&'a Id<Cotonoma>>,
        created_by_id: &'a Id<Node>,
        link: (&'a Id<Coto>, &'a Id<Coto>),
        linking_phrase: Option<&'a str>,
        details: Option<&'a str>,
        order: Option<i32>,
    ) -> Result<Self> {
        let now = crate::current_datetime();
        let new_link = Self {
            uuid: Id::generate(),
            node_id,
            created_in_id,
            created_by_id,
            source_coto_id: link.0,
            target_coto_id: link.1,
            linking_phrase,
            details,
            order,
            created_at: now,
            updated_at: now,
        };
        new_link.validate()?;
        Ok(new_link)
    }

    pub fn source_coto_id(&self) -> &'a Id<Coto> { self.source_coto_id }
}

/////////////////////////////////////////////////////////////////////////////
// UpdateLink
/////////////////////////////////////////////////////////////////////////////

/// A changeset of [Link] for update.
#[derive(Debug, Identifiable, AsChangeset, Validate, new)]
#[diesel(table_name = links, primary_key(uuid))]
pub struct UpdateLink<'a> {
    uuid: &'a Id<Link>,

    #[new(default)]
    #[validate(length(max = "Link::LINKING_PHRASE_MAX_LENGTH"))]
    pub linking_phrase: Option<Option<&'a str>>,

    #[new(default)]
    #[validate(length(max = "Link::DETAILS_MAX_LENGTH"))]
    pub details: Option<Option<&'a str>>,

    #[new(value = "crate::current_datetime()")]
    pub updated_at: NaiveDateTime,
}
