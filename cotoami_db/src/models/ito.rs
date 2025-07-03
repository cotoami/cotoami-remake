//! An [Ito] is a directed edge connecting two [Coto]s.

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
    schema::itos,
};

/////////////////////////////////////////////////////////////////////////////
// Ito
/////////////////////////////////////////////////////////////////////////////

/// A row in `itos` table
#[derive(
    Debug, Clone, PartialEq, Eq, Identifiable, Queryable, serde::Serialize, serde::Deserialize,
)]
#[diesel(primary_key(uuid))]
pub struct Ito {
    /// Universally unique ito ID.
    pub uuid: Id<Ito>,

    /// UUID of the node in which this ito was created.
    pub node_id: Id<Node>,

    /// UUID of the node whose owner has created this ito.
    pub created_by_id: Id<Node>,

    /// UUID of the coto at the source of this ito.
    pub source_coto_id: Id<Coto>,

    /// UUID of the coto at the target of this ito.
    pub target_coto_id: Id<Coto>,

    /// Description of this ito.
    pub description: Option<String>,

    /// Content attached to this ito.
    pub details: Option<String>,

    /// Order of this ito among the ones from the same coto.
    pub order: i32,

    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
}

impl Ito {
    pub const DESCRIPTION_MAX_LENGTH: u64 = 200;
    pub const DETAILS_MAX_LENGTH: u64 = 1_000_000;

    pub fn determine_node(
        node_at_one_end: &Id<Node>,
        node_at_the_other_end: &Id<Node>,
        local_node_id: &Id<Node>,
    ) -> Id<Node> {
        if node_at_one_end == node_at_the_other_end {
            *node_at_one_end
        } else {
            *local_node_id
        }
    }

    pub fn created_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.created_at) }

    pub fn updated_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.updated_at) }

    pub(crate) fn to_import(&self) -> NewIto {
        NewIto {
            uuid: self.uuid,
            node_id: &self.node_id,
            created_by_id: &self.created_by_id,
            source_coto_id: &self.source_coto_id,
            target_coto_id: &self.target_coto_id,
            description: self.description.as_deref(),
            details: self.details.as_deref(),
            order: Some(self.order),
            created_at: self.created_at,
            updated_at: self.updated_at,
        }
    }
}

impl Display for Ito {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let description = self.description.as_deref().unwrap_or_default();
        match self.details.as_deref() {
            Some(details) => write!(
                f,
                "{description} ({})",
                crate::abbreviate_str(details, 5, "…")
                    .as_deref()
                    .unwrap_or(details)
            ),
            None => write!(f, "{description}"),
        }
    }
}

impl BelongsToNode for Ito {
    fn node_id(&self) -> &Id<Node> { &self.node_id }
}

/////////////////////////////////////////////////////////////////////////////
// NewIto
/////////////////////////////////////////////////////////////////////////////

/// An `Insertable` ito data
#[derive(Insertable, Validate)]
#[diesel(table_name = itos)]
pub(crate) struct NewIto<'a> {
    uuid: Id<Ito>,
    node_id: &'a Id<Node>,
    created_by_id: &'a Id<Node>,
    source_coto_id: &'a Id<Coto>,
    target_coto_id: &'a Id<Coto>,
    #[validate(length(max = "Ito::DESCRIPTION_MAX_LENGTH"))]
    description: Option<&'a str>,
    #[validate(length(max = "Ito::DETAILS_MAX_LENGTH"))]
    details: Option<&'a str>,
    #[validate(range(min = 1))]
    pub order: Option<i32>,
    created_at: NaiveDateTime,
    updated_at: NaiveDateTime,
}

impl<'a> NewIto<'a> {
    pub fn new(
        node_id: &'a Id<Node>,
        created_by_id: &'a Id<Node>,
        input: &'a ItoInput<'a>,
    ) -> Result<Self> {
        let now = crate::current_datetime();
        let new_ito = Self {
            uuid: Id::generate(),
            node_id,
            created_by_id,
            source_coto_id: &input.source_coto_id,
            target_coto_id: &input.target_coto_id,
            description: input.description.as_deref(),
            details: input.details.as_deref(),
            order: input.order,
            created_at: now,
            updated_at: now,
        };
        new_ito.validate()?;
        Ok(new_ito)
    }

    pub fn node_id(&self) -> &'a Id<Node> { self.node_id }

    pub fn source_coto_id(&self) -> &'a Id<Coto> { self.source_coto_id }

    pub fn target_coto_id(&self) -> &'a Id<Coto> { self.target_coto_id }
}

/////////////////////////////////////////////////////////////////////////////
// ItoInput
/////////////////////////////////////////////////////////////////////////////

/// Input values to create a new ito as a serializable struct with a builder interface.
#[derive(derive_more::Debug, Clone, serde::Serialize, serde::Deserialize, Validate)]
pub struct ItoInput<'a> {
    pub source_coto_id: Id<Coto>,
    pub target_coto_id: Id<Coto>,

    #[validate(length(max = "Ito::DESCRIPTION_MAX_LENGTH"))]
    pub description: Option<Cow<'a, str>>,

    #[validate(length(max = "Ito::DETAILS_MAX_LENGTH"))]
    pub details: Option<Cow<'a, str>>,

    /// If order is None, the next order number will be assigned automatically.
    #[validate(range(min = 1))]
    pub order: Option<i32>,
}

impl<'a> ItoInput<'a> {
    pub fn new(source_coto_id: Id<Coto>, target_coto_id: Id<Coto>) -> Self {
        Self {
            source_coto_id,
            target_coto_id,
            description: None,
            details: None,
            order: None,
        }
    }

    pub fn description(mut self, description: &'a str) -> Self {
        self.description = Some(Cow::from(description));
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
// UpdateIto
/////////////////////////////////////////////////////////////////////////////

/// A changeset of [Ito] for update.
#[derive(Debug, Identifiable, AsChangeset, Validate, new)]
#[diesel(table_name = itos, primary_key(uuid))]
pub(crate) struct UpdateIto<'a> {
    uuid: &'a Id<Ito>,

    #[new(default)]
    #[validate(length(max = "Ito::DESCRIPTION_MAX_LENGTH"))]
    pub description: Option<Option<&'a str>>,

    #[new(default)]
    #[validate(length(max = "Ito::DETAILS_MAX_LENGTH"))]
    pub details: Option<Option<&'a str>>,

    #[new(value = "crate::current_datetime()")]
    pub updated_at: NaiveDateTime,
}

impl<'a> UpdateIto<'a> {
    pub fn edit_content(&mut self, diff: &'a ItoContentDiff<'a>) {
        self.description = diff
            .description
            .as_ref()
            .map_to_double_option(AsRef::as_ref);

        self.details = diff.details.as_ref().map_to_double_option(AsRef::as_ref);
    }
}

/////////////////////////////////////////////////////////////////////////////
// ItoContentDiff
/////////////////////////////////////////////////////////////////////////////

/// Serializable version of [UpdateIto] as part of public API for ito editing.
#[derive(
    derive_more::Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize, Default, Validate,
)]
pub struct ItoContentDiff<'a> {
    #[validate(length(max = "Ito::DESCRIPTION_MAX_LENGTH"))]
    pub description: FieldDiff<Cow<'a, str>>,

    #[validate(length(max = "Ito::DETAILS_MAX_LENGTH"))]
    pub details: FieldDiff<Cow<'a, str>>,
}

impl<'a> ItoContentDiff<'a> {
    pub fn description(mut self, phrase: Option<&'a str>) -> Self {
        self.description = phrase.into();
        self
    }

    pub fn details(mut self, details: Option<&'a str>) -> Self {
        self.details = details.into();
        self
    }
}
