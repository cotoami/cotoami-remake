//! A coto is a unit of data in a cotoami database.

use std::{borrow::Cow, fmt::Display};

use anyhow::Result;
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use derive_new::new;
use diesel::prelude::*;
use validator::Validate;

use crate::{
    models::{
        cotonoma::Cotonoma,
        node::{BelongsToNode, Node},
        Bytes, Geolocation, Id, Ids,
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
    Identifiable,
    Queryable,
    Selectable,
    serde::Serialize,
    serde::Deserialize,
)]
#[diesel(primary_key(uuid))]
pub struct Coto {
    /// Universally unique coto ID.
    pub uuid: Id<Coto>,

    /// SQLite rowid (so-called "integer primary key")
    /// It is used to return cotos in registration order.
    #[serde(skip_serializing, skip_deserializing)]
    pub rowid: i64,

    /// UUID of the node in which this coto was created.
    pub node_id: Id<Node>,

    /// UUID of the cotonoma in which this coto was posted or
    /// `None` if it is the root cotonoma.
    pub posted_in_id: Option<Id<Cotonoma>>,

    /// UUID of the node whose owner has posted this coto.
    pub posted_by_id: Id<Node>,

    /// Text content of this coto.
    ///
    /// `None` if it is a repost.
    pub content: Option<String>,

    /// Optional summary of the text content for compact display.
    /// If this coto is a cotonoma, the summary should be the same as the cotonoma name.
    pub summary: Option<String>,

    /// Bytes of optional media content.
    #[debug(skip)]
    pub media_content: Option<Bytes>,

    /// MIME type of the media content.
    pub media_type: Option<String>,

    /// TRUE if this coto is a cotonoma.
    pub is_cotonoma: bool,

    pub longitude: Option<f64>,
    pub latitude: Option<f64>,

    /// UUID of the original coto of this repost.
    ///
    /// `None` if it is not a repost.
    pub repost_of_id: Option<Id<Coto>>,

    /// UUIDs of the cotonomas in which this coto was reposted.
    pub reposted_in_ids: Option<Ids<Cotonoma>>,

    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,

    /// Number of outgoing links from this coto.
    pub outgoing_links: i32,
}

impl Coto {
    pub const CONTENT_MAX_LENGTH: u64 = 1_000_000;
    pub const SUMMARY_MAX_LENGTH: u64 = 200;

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

    pub fn media_content(&self) -> Option<(Bytes, String)> {
        if let (Some(content), Some(media_type)) = (&self.media_content, &self.media_type) {
            Some((content.clone(), media_type.clone()))
        } else {
            None
        }
    }

    pub(crate) fn to_update(&self) -> UpdateCoto { UpdateCoto::new(&self.uuid) }

    pub(crate) fn to_import(&self) -> NewCoto {
        NewCoto {
            uuid: self.uuid,
            node_id: &self.node_id,
            posted_in_id: self.posted_in_id.as_ref(),
            posted_by_id: &self.posted_by_id,
            content: self.content.as_deref(),
            media_content: self
                .media_content
                .as_ref()
                .map(|bytes| Cow::from(bytes.as_ref())),
            media_type: self.media_type.as_deref(),
            summary: self.summary.as_deref(),
            is_cotonoma: self.is_cotonoma,
            longitude: self.longitude,
            latitude: self.latitude,
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
#[derive(derive_more::Debug, Insertable, Validate)]
#[diesel(table_name = cotos)]
pub(crate) struct NewCoto<'a> {
    uuid: Id<Coto>,

    node_id: &'a Id<Node>,

    posted_in_id: Option<&'a Id<Cotonoma>>,

    posted_by_id: &'a Id<Node>,

    #[validate(length(max = "Coto::CONTENT_MAX_LENGTH"))]
    content: Option<&'a str>,

    #[validate(length(max = "Coto::SUMMARY_MAX_LENGTH"))]
    summary: Option<&'a str>,

    #[debug(skip)]
    media_content: Option<Cow<'a, [u8]>>,

    media_type: Option<&'a str>,

    is_cotonoma: bool,

    #[validate(range(min = "Geolocation::LONGITUDE_MIN", max = "Geolocation::LONGITUDE_MAX"))]
    longitude: Option<f64>,

    #[validate(range(min = "Geolocation::LATITUDE_MIN", max = "Geolocation::LATITUDE_MAX"))]
    latitude: Option<f64>,

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
            media_content: None,
            media_type: None,
            is_cotonoma: false,
            longitude: None,
            latitude: None,
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
        content: &'a CotoContent<'a>,
        image_max_size: Option<u32>,
    ) -> Result<Self> {
        let mut coto = Self::new_base(node_id, posted_by_id);

        coto.posted_in_id = Some(posted_in_id);
        coto.content = Some(content.content.as_ref());
        coto.summary = content.summary.as_deref();

        if let Some((content, media_type)) = content.media_content.as_ref() {
            let content =
                process_media_content((content.as_ref(), media_type.as_ref()), image_max_size)?;
            coto.media_content = Some(content);
            coto.media_type = Some(media_type.as_ref());
        }

        if let Some(location) = content.geolocation.as_ref() {
            coto.longitude = Some(location.longitude);
            coto.latitude = Some(location.latitude);
        }

        coto.validate()?;
        Ok(coto)
    }

    pub fn new_cotonoma(
        node_id: &'a Id<Node>,
        posted_in_id: &'a Id<Cotonoma>,
        posted_by_id: &'a Id<Node>,
        name: &'a str,
        lng_lat: Option<(f64, f64)>,
    ) -> Result<Self> {
        let mut coto = Self::new_base(node_id, posted_by_id);

        coto.posted_in_id = Some(posted_in_id);
        coto.summary = Some(name); // a cotonoma name is stored as a summary
        coto.is_cotonoma = true;

        if let Some((longitude, latitude)) = lng_lat {
            coto.longitude = Some(longitude);
            coto.latitude = Some(latitude);
        }

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
// CotoContent
/////////////////////////////////////////////////////////////////////////////

/// Grouping coto content inputs as a builder pattern.
#[derive(derive_more::Debug, Clone, serde::Serialize, serde::Deserialize, Validate)]
pub struct CotoContent<'a> {
    #[validate(length(max = "Coto::CONTENT_MAX_LENGTH"))]
    content: Cow<'a, str>,

    #[validate(length(max = "Coto::SUMMARY_MAX_LENGTH"))]
    summary: Option<Cow<'a, str>>,

    /// A pair of media content data and its media type.
    // TODO: needs validation?
    #[debug(skip)]
    media_content: Option<(Bytes, Cow<'a, str>)>,

    #[validate(nested)]
    geolocation: Option<Geolocation>,
}

impl<'a> CotoContent<'a> {
    pub fn new(content: &'a str) -> Self {
        Self {
            content: Cow::from(content),
            summary: None,
            media_content: None,
            geolocation: None,
        }
    }

    pub fn summary(mut self, summary: &'a str) -> Self {
        self.summary = Some(Cow::from(summary));
        self
    }

    pub fn media_content(mut self, content: Bytes, content_type: &'a str) -> Self {
        self.media_content = Some((content, Cow::from(content_type)));
        self
    }

    pub fn geolocation(mut self, geolocation: Geolocation) -> Self {
        self.geolocation = Some(geolocation);
        self
    }
}

/////////////////////////////////////////////////////////////////////////////
// UpdateCoto
/////////////////////////////////////////////////////////////////////////////

/// A changeset of [Coto] for update.
/// Only fields that have [Some] value will be updated.
#[derive(derive_more::Debug, Identifiable, AsChangeset, Validate, new)]
#[diesel(table_name = cotos, primary_key(uuid))]
pub(crate) struct UpdateCoto<'a> {
    uuid: &'a Id<Coto>,

    #[new(default)]
    #[validate(length(max = "Coto::CONTENT_MAX_LENGTH"))]
    pub content: Option<Option<&'a str>>,

    #[new(default)]
    #[validate(length(max = "Coto::SUMMARY_MAX_LENGTH"))]
    pub summary: Option<Option<&'a str>>,

    #[debug(skip)]
    #[new(default)]
    pub media_content: Option<Option<Cow<'a, [u8]>>>,

    #[new(default)]
    pub media_type: Option<Option<&'a str>>,

    #[new(default)]
    pub is_cotonoma: Option<bool>,

    #[new(default)]
    pub longitude: Option<Option<f64>>,

    #[new(default)]
    pub latitude: Option<Option<f64>>,

    #[new(default)]
    pub repost_of_id: Option<Option<&'a Id<Coto>>>,

    #[new(default)]
    pub reposted_in_ids: Option<Option<&'a Ids<Cotonoma>>>,

    #[new(value = "crate::current_datetime()")]
    pub updated_at: NaiveDateTime,
}

impl<'a> UpdateCoto<'a> {
    pub fn edit_content(
        &mut self,
        diff: &'a CotoContentDiff<'a>,
        image_max_size: Option<u32>,
    ) -> Result<()> {
        self.content = Some(diff.content.as_deref());
        self.summary = diff
            .summary
            .as_ref()
            .map(|s| crate::blank_to_none(s.as_deref()));

        match diff.media_content.as_ref() {
            Some(Some((content, media_type))) => {
                let media_type = media_type.as_ref();
                let content =
                    process_media_content((content.as_ref(), media_type), image_max_size)?;
                self.media_content = Some(Some(content));
                self.media_type = Some(Some(media_type));
            }
            Some(None) => {
                self.media_content = Some(None);
                self.media_type = Some(None);
            }
            None => {
                self.media_content = None;
                self.media_type = None;
            }
        }

        match diff.geolocation.as_ref() {
            Some(Some(geolocation)) => {
                self.longitude = Some(Some(geolocation.longitude));
                self.latitude = Some(Some(geolocation.latitude));
            }
            Some(None) => {
                self.longitude = Some(None);
                self.latitude = Some(None);
            }
            None => {
                self.longitude = None;
                self.latitude = None;
            }
        }

        Ok(())
    }
}

/////////////////////////////////////////////////////////////////////////////
// CotoContentDiff
/////////////////////////////////////////////////////////////////////////////

#[derive(
    derive_more::Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize, Default, Validate,
)]
pub struct CotoContentDiff<'a> {
    #[validate(length(max = "Coto::CONTENT_MAX_LENGTH"))]
    pub content: Option<Cow<'a, str>>,

    #[validate(length(max = "Coto::SUMMARY_MAX_LENGTH"))]
    pub summary: Option<Option<Cow<'a, str>>>,

    pub media_content: Option<Option<(Bytes, Cow<'a, str>)>>,

    #[validate(nested)]
    pub geolocation: Option<Option<Geolocation>>,
}

impl<'a> CotoContentDiff<'a> {
    pub fn content(mut self, content: &'a str) -> Self {
        self.content = Some(Cow::from(content));
        self
    }

    pub fn summary(mut self, summary: Option<&'a str>) -> Self {
        self.summary = Some(summary.map(Cow::from));
        self
    }

    pub fn media_content(mut self, content: Option<(Bytes, &'a str)>) -> Self {
        self.media_content = Some(content.map(|c| (c.0, Cow::from(c.1))));
        self
    }

    pub fn geolocation(mut self, geolocation: Option<Geolocation>) -> Self {
        self.geolocation = Some(geolocation);
        self
    }
}

/////////////////////////////////////////////////////////////////////////////
// Internal functions
/////////////////////////////////////////////////////////////////////////////

fn process_media_content<'a>(
    media_content: (&'a [u8], &'a str),
    image_max_size: Option<u32>,
) -> Result<Cow<'a, [u8]>> {
    let (content, media_type) = media_content;
    if media_type.starts_with("image/") {
        if let Some(max_size) = image_max_size {
            let resized = super::resize_image(content, max_size, None)?;
            Ok(Cow::from(resized))
        } else {
            Ok(Cow::from(content))
        }
    } else {
        Ok(Cow::from(content))
    }
}
