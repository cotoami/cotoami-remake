//! A coto is a unit of data in a cotoami database.

use std::{borrow::Cow, convert::AsRef, fmt::Display};

use anyhow::{bail, Result};
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use derive_new::new;
use diesel::prelude::*;
use validator::Validate;

use crate::{
    models::{
        cotonoma::{Cotonoma, CotonomaInput},
        node::{BelongsToNode, Node},
        Bytes, DateTimeRange, FieldDiff, Geolocation, Id, Ids,
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

    /// Geolocation
    pub longitude: Option<f64>,
    pub latitude: Option<f64>,

    /// DateTime range
    pub datetime_start: Option<NaiveDateTime>,
    pub datetime_end: Option<NaiveDateTime>,

    /// UUID of the original coto of this repost.
    ///
    /// `None` if it is not a repost.
    pub repost_of_id: Option<Id<Coto>>,

    /// UUIDs of the cotonomas in which this coto was reposted.
    pub reposted_in_ids: Option<Ids<Cotonoma>>,

    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
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

    pub fn posted_in(&self, cotonoma_id: &Id<Cotonoma>) -> bool {
        self.posted_in_id == Some(*cotonoma_id)
            || self
                .reposted_in_ids
                .as_ref()
                .map(|ids| ids.contains(cotonoma_id))
                .unwrap_or(false)
    }

    pub fn is_repost(&self) -> bool { self.repost_of_id.is_some() }

    pub(crate) fn to_update(&self) -> UpdateCoto { UpdateCoto::new(&self.uuid) }

    pub(crate) fn to_import(&self) -> Result<NewCoto> {
        // Since it can't import reposts before the original and
        // `reposted_in_ids` will be updated when inserting a repost,
        // `reposted_in_ids` must be None for import.
        if self.reposted_in_ids.is_some() {
            bail!("Coto::reposted_in_ids must be None for import.");
        }
        Ok(NewCoto {
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
            datetime_start: self.datetime_start,
            datetime_end: self.datetime_end,
            repost_of_id: self.repost_of_id.as_ref(),
            reposted_in_ids: None,
            created_at: self.created_at,
            updated_at: self.updated_at,
        })
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

    datetime_start: Option<NaiveDateTime>,
    datetime_end: Option<NaiveDateTime>,

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
            datetime_start: None,
            datetime_end: None,
            repost_of_id: None,
            reposted_in_ids: None,
            created_at: now,
            updated_at: now,
        }
    }

    fn set_geolocation(&mut self, location: &Geolocation) {
        self.longitude = Some(location.longitude);
        self.latitude = Some(location.latitude);
    }

    fn set_datetime_range(&mut self, datetime_range: &DateTimeRange) {
        self.datetime_start = Some(datetime_range.start);
        self.datetime_end = datetime_range.end;
    }

    pub fn new(
        node_id: &'a Id<Node>,
        posted_in_id: &'a Id<Cotonoma>,
        posted_by_id: &'a Id<Node>,
        input: &'a CotoInput<'a>,
        image_max_size: Option<u32>,
    ) -> Result<Self> {
        let mut coto = Self::new_base(node_id, posted_by_id);

        coto.posted_in_id = Some(posted_in_id);
        coto.content = Some(input.content.as_ref());
        coto.summary = input.summary.as_deref();

        if let Some((content, media_type)) = input.media_content.as_ref() {
            let content =
                process_media_content((content.as_ref(), media_type.as_ref()), image_max_size)?;
            coto.media_content = Some(content);
            coto.media_type = Some(media_type.as_ref());
        }

        if let Some(location) = input.geolocation.as_ref() {
            coto.set_geolocation(location);
        }

        if let Some(datetime_range) = input.datetime_range.as_ref() {
            coto.set_datetime_range(datetime_range);
        }

        coto.validate()?;
        Ok(coto)
    }

    pub fn new_cotonoma(
        node_id: &'a Id<Node>,
        posted_in_id: &'a Id<Cotonoma>,
        posted_by_id: &'a Id<Node>,
        input: &'a CotonomaInput<'a>,
    ) -> Result<Self> {
        let mut coto = Self::new_base(node_id, posted_by_id);

        coto.posted_in_id = Some(posted_in_id);
        coto.summary = Some(input.name.as_ref()); // a cotonoma name is stored as a summary
        coto.is_cotonoma = true;

        if let Some(location) = input.geolocation.as_ref() {
            coto.set_geolocation(location);
        }

        if let Some(datetime_range) = input.datetime_range.as_ref() {
            coto.set_datetime_range(datetime_range);
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

    pub fn new_repost(
        original_id: &'a Id<Coto>,
        dest: &'a Cotonoma,
        reposted_by: &'a Id<Node>,
    ) -> Self {
        let mut coto = Self::new_base(&dest.node_id, reposted_by);
        coto.repost_of_id = Some(original_id);
        coto.posted_in_id = Some(&dest.uuid);
        coto
    }

    pub fn set_timestamp(&mut self, timestamp: NaiveDateTime) {
        self.created_at = timestamp;
        self.updated_at = timestamp;
    }

    pub fn posted_in_id(&self) -> Option<&'a Id<Cotonoma>> { self.posted_in_id }
}

/////////////////////////////////////////////////////////////////////////////
// CotoInput
/////////////////////////////////////////////////////////////////////////////

/// Input values to create a new coto as a serializable struct with a builder interface.
#[derive(derive_more::Debug, Clone, serde::Serialize, serde::Deserialize, Validate)]
pub struct CotoInput<'a> {
    #[validate(length(max = "Coto::CONTENT_MAX_LENGTH"))]
    pub content: Cow<'a, str>,

    #[validate(length(max = "Coto::SUMMARY_MAX_LENGTH"))]
    pub summary: Option<Cow<'a, str>>,

    /// A pair of media content data and its media type.
    // TODO: needs validation?
    #[debug(skip)]
    pub media_content: Option<(Bytes, Cow<'a, str>)>,

    #[validate(nested)]
    pub geolocation: Option<Geolocation>,

    pub datetime_range: Option<DateTimeRange>,
}

impl<'a> CotoInput<'a> {
    pub fn new(content: &'a str) -> Self {
        Self {
            content: Cow::from(content),
            summary: None,
            media_content: None,
            geolocation: None,
            datetime_range: None,
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

    pub fn datetime_range(mut self, datetime_range: DateTimeRange) -> Self {
        self.datetime_range = Some(datetime_range);
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
    pub datetime_start: Option<Option<NaiveDateTime>>,

    #[new(default)]
    pub datetime_end: Option<Option<NaiveDateTime>>,

    #[new(default)]
    pub reposted_in_ids: Option<Option<Ids<Cotonoma>>>,

    #[new(value = "crate::current_datetime()")]
    pub updated_at: NaiveDateTime,
}

impl<'a> UpdateCoto<'a> {
    pub fn edit_content(
        &mut self,
        diff: &'a CotoContentDiff<'a>,
        image_max_size: Option<u32>,
    ) -> Result<()> {
        self.content = diff.content.as_ref().map_to_double_option(AsRef::as_ref);

        self.summary = match diff.summary.as_ref() {
            FieldDiff::None => None,
            FieldDiff::Delete => Some(None),
            FieldDiff::Change(s) => Some(crate::blank_to_none(Some(s.as_ref()))),
        };

        match diff.media_content.as_ref() {
            FieldDiff::None => {
                self.media_content = None;
                self.media_type = None;
            }
            FieldDiff::Delete => {
                self.media_content = Some(None);
                self.media_type = Some(None);
            }
            FieldDiff::Change((content, media_type)) => {
                let media_type = media_type.as_ref();
                let content =
                    process_media_content((content.as_ref(), media_type), image_max_size)?;
                self.media_content = Some(Some(content));
                self.media_type = Some(Some(media_type));
            }
        }

        match diff.geolocation.as_ref() {
            FieldDiff::None => {
                self.longitude = None;
                self.latitude = None;
            }
            FieldDiff::Delete => {
                self.longitude = Some(None);
                self.latitude = Some(None);
            }
            FieldDiff::Change(location) => {
                self.longitude = Some(Some(location.longitude));
                self.latitude = Some(Some(location.latitude));
            }
        }

        match diff.datetime_range.as_ref() {
            FieldDiff::None => {
                self.datetime_start = None;
                self.datetime_end = None;
            }
            FieldDiff::Delete => {
                self.datetime_start = Some(None);
                self.datetime_end = Some(None);
            }
            FieldDiff::Change(datetime_range) => {
                self.datetime_start = Some(Some(datetime_range.start));
                self.datetime_end = Some(datetime_range.end);
            }
        }

        Ok(())
    }

    pub fn repost_in(&mut self, cotonoma_id: Id<Cotonoma>, original: &Coto) {
        if let Some(ref reposted_in_ids) = original.reposted_in_ids {
            let mut reposted_in_ids = reposted_in_ids.clone();
            reposted_in_ids.add(cotonoma_id);
            self.reposted_in_ids = Some(Some(reposted_in_ids))
        } else {
            self.reposted_in_ids = Some(Some(Ids::from_one(cotonoma_id)))
        }
    }

    pub fn remove_reposted_in(&mut self, cotonoma_id: &Id<Cotonoma>, original: &Coto) {
        if let Some(ref reposted_in_ids) = original.reposted_in_ids {
            let mut reposted_in_ids = reposted_in_ids.clone();
            reposted_in_ids.remove(cotonoma_id);
            if reposted_in_ids.is_empty() {
                self.reposted_in_ids = Some(None);
            } else {
                self.reposted_in_ids = Some(Some(reposted_in_ids));
            }
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// CotoContentDiff
/////////////////////////////////////////////////////////////////////////////

/// Serializable version of [UpdateCoto] as part of public API for coto editing.
#[derive(
    derive_more::Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize, Default, Validate,
)]
pub struct CotoContentDiff<'a> {
    #[validate(length(max = "Coto::CONTENT_MAX_LENGTH"))]
    pub content: FieldDiff<Cow<'a, str>>,

    #[validate(length(max = "Coto::SUMMARY_MAX_LENGTH"))]
    pub summary: FieldDiff<Cow<'a, str>>,

    pub media_content: FieldDiff<(Bytes, Cow<'a, str>)>,

    #[validate(nested)]
    pub geolocation: FieldDiff<Geolocation>,

    pub datetime_range: FieldDiff<DateTimeRange>,
}

impl<'a> CotoContentDiff<'a> {
    pub fn content(mut self, content: &'a str) -> Self {
        self.content = FieldDiff::Change(Cow::from(content));
        self
    }

    pub fn summary(mut self, summary: Option<&'a str>) -> Self {
        self.summary = summary.into();
        self
    }

    pub fn media_content(mut self, content: Option<(Bytes, &'a str)>) -> Self {
        self.media_content = if let Some((c, t)) = content {
            FieldDiff::Change((c, Cow::from(t)))
        } else {
            FieldDiff::Delete
        };
        self
    }

    pub fn geolocation(mut self, geolocation: Option<Geolocation>) -> Self {
        self.geolocation = geolocation.into();
        self
    }

    pub fn datetime_range(mut self, datetime_range: Option<DateTimeRange>) -> Self {
        self.datetime_range = datetime_range.into();
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
        super::process_image(content, image_max_size, None)
    } else {
        Ok(Cow::from(content))
    }
}

/////////////////////////////////////////////////////////////////////////////
// tests
/////////////////////////////////////////////////////////////////////////////

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use googletest::prelude::*;
    use indoc::indoc;

    use super::*;

    #[test]
    fn serde_diff() -> Result<()> {
        let diff = CotoContentDiff::default().content("hello").summary(None);

        let msgpack = rmp_serde::to_vec(&diff)?;
        let deserialized: CotoContentDiff = rmp_serde::from_slice(&msgpack)?;
        assert_that!(
            deserialized,
            pat!(CotoContentDiff {
                content: pat!(FieldDiff::Change(eq("hello"))),
                summary: eq(&FieldDiff::Delete),
                media_content: eq(&FieldDiff::None),
                geolocation: eq(&FieldDiff::None)
            })
        );

        Ok(())
    }

    #[test]
    fn diff_json() -> Result<()> {
        let diff = CotoContentDiff::default().content("hello").summary(None);
        let json_string = serde_json::to_string_pretty(&diff).unwrap();
        assert_eq!(
            json_string,
            indoc! {r#"
            {
              "content": {
                "Change": "hello"
              },
              "summary": "Delete",
              "media_content": "None",
              "geolocation": "None",
              "datetime_range": "None"
            }"#}
        );
        Ok(())
    }
}
