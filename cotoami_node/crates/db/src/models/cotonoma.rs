//! A cotonoma is a specific type of [Coto] in which other cotos are posted.

use std::borrow::Cow;

use anyhow::Result;
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use derive_new::new;
use diesel::prelude::*;
use validator::Validate;

use super::{
    coto::Coto,
    node::{BelongsToNode, Node},
    DateTimeRange, Geolocation, Id,
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
}

impl Cotonoma {
    pub const NAME_MAX_LENGTH: u64 = 50;

    pub fn created_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.created_at) }

    pub fn updated_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.updated_at) }

    pub(crate) fn to_update(&self) -> UpdateCotonoma { UpdateCotonoma::new(&self.uuid) }

    pub(crate) fn to_import(&self) -> NewCotonoma {
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
pub(crate) struct NewCotonoma<'a> {
    uuid: Id<Cotonoma>,
    node_id: &'a Id<Node>,
    coto_id: &'a Id<Coto>,
    #[validate(length(max = "Cotonoma::NAME_MAX_LENGTH"))]
    name: &'a str,
    created_at: NaiveDateTime,
    updated_at: NaiveDateTime,
}

impl<'a> NewCotonoma<'a> {
    pub fn new(
        node_id: &'a Id<Node>,
        coto_id: &'a Id<Coto>,
        name: &'a str,
        created_at: NaiveDateTime,
    ) -> Result<Self> {
        let cotonoma = Self {
            uuid: Id::generate(),
            node_id,
            coto_id,
            name,
            created_at,
            updated_at: created_at,
        };
        cotonoma.validate()?;
        Ok(cotonoma)
    }

    pub fn promoted_from(
        coto: &'a Coto,
        promoted_at: NaiveDateTime,
        cotonoma_id: Option<Id<Cotonoma>>,
    ) -> Result<Self> {
        let cotonoma = Self {
            uuid: cotonoma_id.unwrap_or(Id::generate()),
            node_id: &coto.node_id,
            coto_id: &coto.uuid,
            name: coto.name_as_cotonoma().unwrap(),
            created_at: promoted_at,
            updated_at: promoted_at,
        };
        cotonoma.validate()?;
        Ok(cotonoma)
    }
}

/////////////////////////////////////////////////////////////////////////////
// CotonomaInput
/////////////////////////////////////////////////////////////////////////////

/// Cotonoma input values as a serializable struct with a builder interface.
#[derive(derive_more::Debug, Clone, serde::Serialize, serde::Deserialize, Validate)]
pub struct CotonomaInput<'a> {
    #[validate(length(max = "Cotonoma::NAME_MAX_LENGTH"))]
    pub name: Cow<'a, str>,

    #[validate(nested)]
    pub geolocation: Option<Geolocation>,

    pub datetime_range: Option<DateTimeRange>,
}

impl<'a> CotonomaInput<'a> {
    pub fn new(name: &'a str) -> Self {
        Self {
            name: Cow::from(name),
            geolocation: None,
            datetime_range: None,
        }
    }

    pub fn geolocation(mut self, geolocation: Geolocation) -> Self {
        self.geolocation = Some(geolocation);
        self
    }

    pub fn datetime_range(mut self, datetime_range: DateTimeRange) -> Self {
        self.datetime_range = Some(datetime_range);
        self
    }
}

/////////////////////////////////////////////////////////////////////////////
// UpdateCotonoma
/////////////////////////////////////////////////////////////////////////////

/// A changeset of [Cotonoma] for update.
#[derive(Debug, Identifiable, AsChangeset, Validate, new)]
#[diesel(table_name = cotonomas, primary_key(uuid))]
pub(crate) struct UpdateCotonoma<'a> {
    uuid: &'a Id<Cotonoma>,

    #[new(default)]
    #[validate(length(max = "Cotonoma::NAME_MAX_LENGTH"))]
    pub name: Option<&'a str>,

    #[new(value = "crate::current_datetime()")]
    pub updated_at: NaiveDateTime,
}
