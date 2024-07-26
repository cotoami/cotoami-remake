//! A coto is a unit of data in a cotoami database.

use std::fmt::Display;

use anyhow::Result;
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use derive_new::new;
use diesel::prelude::*;
use validator::Validate;

use crate::{
    models::{
        cotonoma::Cotonoma,
        node::{BelongsToNode, Node},
        Bytes, Id, Ids,
    },
    schema::cotos,
};

/////////////////////////////////////////////////////////////////////////////
// Coto
/////////////////////////////////////////////////////////////////////////////

/// A row in `cotos` table
#[derive(
    derive_more::Debug,
    Clone,
    PartialEq,
    Eq,
    Identifiable,
    Queryable,
    Selectable,
    serde::Serialize,
    serde::Deserialize,
)]
#[diesel(primary_key(uuid))]
pub struct Coto {
    /// Universally unique coto ID
    pub uuid: Id<Coto>,

    /// SQLite rowid (so-called "integer primary key")
    /// It is used to return cotos in registration order.
    #[serde(skip_serializing, skip_deserializing)]
    pub rowid: i64,

    /// UUID of the node in which this coto was created
    pub node_id: Id<Node>,

    /// UUID of the cotonoma in which this coto was posted or
    /// `None` if it is the root cotonoma.
    pub posted_in_id: Option<Id<Cotonoma>>,

    /// UUID of the node whose owner has posted this coto
    pub posted_by_id: Id<Node>,

    /// Content of this coto
    ///
    /// `None` if it is a repost.
    pub content: Option<String>,

    /// Bytes of optional media content.
    #[debug(skip)]
    pub media_content: Option<Bytes>,

    /// MIME type of the media content.
    pub media_type: Option<String>,

    /// Optional summary of the content for compact display
    /// If this coto is a cotonoma, the summary should be the same as the cotonoma name.
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

    /// Number of outgoing links from this coto
    pub outgoing_links: i32,
}

impl Coto {
    pub const CONTENT_MAX_LENGTH: usize = 1_000_000;
    pub const SUMMARY_MAX_LENGTH: usize = 200;

    pub fn created_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.created_at) }

    pub fn updated_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.updated_at) }

    /// Returns the cotonoma name if this coto is cotonoma
    pub fn name_as_cotonoma(&self) -> Option<&str> {
        if self.is_cotonoma {
            // The summary should be the same as the cotonoma name.
            // The summary might be NULL if the data has been imported from the previous versions,
            // so, to support that case, the content will be used instead.
            self.summary.as_deref().or(self.content.as_deref())
        } else {
            None
        }
    }

    pub fn edit<'a>(&'a self, content: &'a str, summary: Option<&'a str>) -> UpdateCoto<'a> {
        let mut update_coto = self.to_update();
        update_coto.content = Some(Some(content));
        update_coto.summary = Some(crate::blank_to_none(summary));
        update_coto
    }

    pub fn to_update(&self) -> UpdateCoto { UpdateCoto::new(&self.uuid) }

    pub fn to_import(&self) -> NewCoto {
        NewCoto {
            uuid: self.uuid,
            node_id: &self.node_id,
            posted_in_id: self.posted_in_id.as_ref(),
            posted_by_id: &self.posted_by_id,
            content: self.content.as_deref(),
            summary: self.summary.as_deref(),
            is_cotonoma: self.is_cotonoma,
            repost_of_id: self.repost_of_id.as_ref(),
            reposted_in_ids: self.reposted_in_ids.as_ref(),
            created_at: self.created_at,
            updated_at: self.updated_at,
        }
    }
}

impl Display for Coto {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        if self.is_cotonoma {
            write!(f, "<{}>", self.name_as_cotonoma().unwrap_or_default())
        } else {
            let content = self.content.as_deref().unwrap_or_default();
            match self.summary.as_deref() {
                Some(summary) => {
                    write!(
                        f,
                        "{summary} ({})",
                        crate::abbreviate_str(content, 5, "…")
                            .as_deref()
                            .unwrap_or(content)
                    )
                }
                None => write!(f, "{content}"),
            }
        }
    }
}

impl BelongsToNode for Coto {
    fn node_id(&self) -> &Id<Node> { &self.node_id }
}

/////////////////////////////////////////////////////////////////////////////
// NewCoto
/////////////////////////////////////////////////////////////////////////////

/// An `Insertable` coto data
#[derive(Debug, Insertable, Validate)]
#[diesel(table_name = cotos)]
pub struct NewCoto<'a> {
    uuid: Id<Coto>,
    node_id: &'a Id<Node>,
    posted_in_id: Option<&'a Id<Cotonoma>>,
    posted_by_id: &'a Id<Node>,
    #[validate(length(max = "Coto::CONTENT_MAX_LENGTH"))]
    content: Option<&'a str>,
    #[validate(length(max = "Coto::SUMMARY_MAX_LENGTH"))]
    summary: Option<&'a str>,
    is_cotonoma: bool,
    repost_of_id: Option<&'a Id<Coto>>,
    reposted_in_ids: Option<&'a Ids<Cotonoma>>,
    created_at: NaiveDateTime,
    updated_at: NaiveDateTime,
}

impl<'a> NewCoto<'a> {
    fn new_base(node_id: &'a Id<Node>, posted_by_id: &'a Id<Node>) -> Self {
        let now = crate::current_datetime();
        Self {
            uuid: Id::generate(),
            node_id,
            posted_in_id: None,
            posted_by_id,
            content: None,
            summary: None,
            is_cotonoma: false,
            repost_of_id: None,
            reposted_in_ids: None,
            created_at: now,
            updated_at: now,
        }
    }

    pub fn new(
        node_id: &'a Id<Node>,
        posted_in_id: &'a Id<Cotonoma>,
        posted_by_id: &'a Id<Node>,
        content: &'a str,
        summary: Option<&'a str>,
    ) -> Result<Self> {
        let mut coto = Self::new_base(node_id, posted_by_id);
        coto.posted_in_id = Some(posted_in_id);
        coto.content = Some(content);
        coto.summary = summary;
        coto.validate()?;
        Ok(coto)
    }

    pub fn new_cotonoma(
        node_id: &'a Id<Node>,
        posted_in_id: &'a Id<Cotonoma>,
        posted_by_id: &'a Id<Node>,
        name: &'a str,
    ) -> Result<Self> {
        let mut coto = Self::new_base(node_id, posted_by_id);
        coto.posted_in_id = Some(posted_in_id);
        coto.summary = Some(name); // a cotonoma name is stored as a summary
        coto.is_cotonoma = true;
        coto.validate()?;
        Ok(coto)
    }

    pub fn new_root_cotonoma(node_id: &'a Id<Node>, name: &'a str) -> Result<Self> {
        let mut coto = Self::new_base(node_id, node_id);
        coto.summary = Some(name); // a cotonoma name is stored as a summary
        coto.is_cotonoma = true;
        coto.validate()?;
        Ok(coto)
    }
}

/////////////////////////////////////////////////////////////////////////////
// UpdateCoto
/////////////////////////////////////////////////////////////////////////////

/// A changeset of a coto for update.
/// Only fields that have [Some] value will be updated.
#[derive(Debug, Identifiable, AsChangeset, Validate, new)]
#[diesel(table_name = cotos, primary_key(uuid))]
pub struct UpdateCoto<'a> {
    uuid: &'a Id<Coto>,

    #[new(default)]
    #[validate(length(max = "Coto::CONTENT_MAX_LENGTH"))]
    pub content: Option<Option<&'a str>>,

    #[new(default)]
    #[validate(length(max = "Coto::SUMMARY_MAX_LENGTH"))]
    pub summary: Option<Option<&'a str>>,

    #[new(default)]
    pub is_cotonoma: Option<bool>,

    #[new(default)]
    pub repost_of_id: Option<Option<&'a Id<Coto>>>,

    #[new(default)]
    pub reposted_in_ids: Option<Option<&'a Ids<Cotonoma>>>,

    #[new(value = "crate::current_datetime()")]
    pub updated_at: NaiveDateTime,
}
