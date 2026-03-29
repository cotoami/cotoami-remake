//! Dedicated schema for `CotoInput`.
//!
//! `CotoInput` uses borrowed `Cow` fields internally. This schema owns its
//! payload so the wire contract is independent from in-process borrowing.

use std::borrow::Cow;

use cotoami_db::{models::DateTimeRange, prelude::*};
use serde::{Deserialize, Serialize};

use super::MediaContentSchema;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CotoInputSchema {
    pub content: String,
    #[serde(default)]
    pub summary: Option<String>,
    #[serde(default)]
    pub media_content: Option<MediaContentSchema>,
    #[serde(default)]
    pub geolocation: Option<Geolocation>,
    #[serde(default)]
    pub datetime_range: Option<DateTimeRange>,
}

impl From<CotoInput<'static>> for CotoInputSchema {
    fn from(input: CotoInput<'static>) -> Self {
        Self {
            content: input.content.into_owned(),
            summary: input.summary.map(Cow::into_owned),
            media_content: input.media_content.map(|(content, media_type)| MediaContentSchema {
                content,
                media_type: media_type.into_owned(),
            }),
            geolocation: input.geolocation,
            datetime_range: input.datetime_range,
        }
    }
}

impl From<CotoInputSchema> for CotoInput<'static> {
    fn from(input: CotoInputSchema) -> Self {
        Self {
            content: Cow::Owned(input.content),
            summary: input.summary.map(Cow::Owned),
            media_content: input
                .media_content
                .map(|media| (media.content, Cow::Owned(media.media_type))),
            geolocation: input.geolocation,
            datetime_range: input.datetime_range,
        }
    }
}
