//! A link is a directed edge connecting two [Coto]s.

use std::{borrow::Cow, fmt::Display};

use anyhow::Result;
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use derive_new::new;
use diesel::prelude::*;
use validator::Validate;

use crate::{
    models::{
        coto::Coto,
        node::{BelongsToNode, Node},
        FieldDiff, Id,
    },
    schema::links,
};

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
    pub const LINKING_PHRASE_MAX_LENGTH: u64 = 200;
    pub const DETAILS_MAX_LENGTH: u64 = 1_000_000;

    pub fn created_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.created_at) }

    pub fn updated_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.updated_at) }

    pub(crate) fn to_import(&self) -> NewLink {
        NewLink {
            uuid: self.uuid,
            node_id: &self.node_id,
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
pub(crate) struct NewLink<'a> {
    uuid: Id<Link>,
    node_id: &'a Id<Node>,
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
        created_by_id: &'a Id<Node>,
        input: &'a LinkInput<'a>,
    ) -> Result<Self> {
        let now = crate::current_datetime();
        let new_link = Self {
            uuid: Id::generate(),
            node_id,
            created_by_id,
            source_coto_id: &input.source_coto_id,
            target_coto_id: &input.target_coto_id,
            linking_phrase: input.linking_phrase.as_deref(),
            details: input.details.as_deref(),
            order: input.order,
            created_at: now,
            updated_at: now,
        };
        new_link.validate()?;
        Ok(new_link)
    }

    pub fn source_coto_id(&self) -> &'a Id<Coto> { self.source_coto_id }
}

/////////////////////////////////////////////////////////////////////////////
// LinkInput
/////////////////////////////////////////////////////////////////////////////

/// Input values to create a new link as a serializable struct with a builder interface.
#[derive(derive_more::Debug, Clone, serde::Serialize, serde::Deserialize, Validate)]
pub struct LinkInput<'a> {
    pub source_coto_id: Id<Coto>,
    pub target_coto_id: Id<Coto>,

    #[validate(length(max = "Link::LINKING_PHRASE_MAX_LENGTH"))]
    pub linking_phrase: Option<Cow<'a, str>>,

    #[validate(length(max = "Link::DETAILS_MAX_LENGTH"))]
    pub details: Option<Cow<'a, str>>,

    /// If order is None, the next order number will be assigned automatically.
    #[validate(range(min = 1))]
    pub order: Option<i32>,
}

impl<'a> LinkInput<'a> {
    pub fn new(source_coto_id: Id<Coto>, target_coto_id: Id<Coto>) -> Self {
        Self {
            source_coto_id,
            target_coto_id,
            linking_phrase: None,
            details: None,
            order: None,
        }
    }

    pub fn linking_phrase(mut self, linking_phrase: &'a str) -> Self {
        self.linking_phrase = Some(Cow::from(linking_phrase));
        self
    }

    pub fn details(mut self, details: &'a str) -> Self {
        self.details = Some(Cow::from(details));
        self
    }

    pub fn order(mut self, order: i32) -> Self {
        self.order = Some(order);
        self
    }
}

/////////////////////////////////////////////////////////////////////////////
// UpdateLink
/////////////////////////////////////////////////////////////////////////////

/// A changeset of [Link] for update.
#[derive(Debug, Identifiable, AsChangeset, Validate, new)]
#[diesel(table_name = links, primary_key(uuid))]
pub(crate) struct UpdateLink<'a> {
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

impl<'a> UpdateLink<'a> {
    pub fn edit_content(&mut self, diff: &'a LinkContentDiff<'a>) {
        self.linking_phrase = diff
            .linking_phrase
            .as_ref()
            .map_to_double_option(AsRef::as_ref);

        self.details = diff.details.as_ref().map_to_double_option(AsRef::as_ref);
    }
}

/////////////////////////////////////////////////////////////////////////////
// LinkContentDiff
/////////////////////////////////////////////////////////////////////////////

/// Serializable version of [UpdateLink] as part of public API for link editing.
#[derive(
    derive_more::Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize, Default, Validate,
)]
pub struct LinkContentDiff<'a> {
    #[validate(length(max = "Link::LINKING_PHRASE_MAX_LENGTH"))]
    pub linking_phrase: FieldDiff<Cow<'a, str>>,

    #[validate(length(max = "Link::DETAILS_MAX_LENGTH"))]
    pub details: FieldDiff<Cow<'a, str>>,
}

impl<'a> LinkContentDiff<'a> {
    pub fn linking_phrase(mut self, phrase: Option<&'a str>) -> Self {
        self.linking_phrase = phrase.into();
        self
    }

    pub fn details(mut self, details: Option<&'a str>) -> Self {
        self.details = details.into();
        self
    }
}
