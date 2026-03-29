//! Dedicated schema for `CotoContentDiff`.
//!
//! The internal diff carries borrowed strings and transport-sensitive field
//! diff values. This schema owns its data and fixes the wire-visible shape.

use cotoami_db::{models::DateTimeRange, prelude::*};
use serde::{Deserialize, Serialize};

use super::{
    field_diff::{
        field_diff_from_schema, field_diff_to_schema, media_field_diff_from_schema,
        media_field_diff_to_schema, string_field_diff_from_schema, string_field_diff_to_schema,
    },
    FieldDiffSchema, MediaContentSchema,
};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CotoContentDiffSchema {
    pub content: FieldDiffSchema<String>,
    pub summary: FieldDiffSchema<String>,
    pub media_content: FieldDiffSchema<MediaContentSchema>,
    pub geolocation: FieldDiffSchema<Geolocation>,
    pub datetime_range: FieldDiffSchema<DateTimeRange>,
}

impl From<CotoContentDiff<'static>> for CotoContentDiffSchema {
    fn from(diff: CotoContentDiff<'static>) -> Self {
        Self {
            content: string_field_diff_to_schema(diff.content),
            summary: string_field_diff_to_schema(diff.summary),
            media_content: media_field_diff_to_schema(diff.media_content),
            geolocation: field_diff_to_schema(diff.geolocation),
            datetime_range: field_diff_to_schema(diff.datetime_range),
        }
    }
}

impl From<CotoContentDiffSchema> for CotoContentDiff<'static> {
    fn from(diff: CotoContentDiffSchema) -> Self {
        Self {
            content: string_field_diff_from_schema(diff.content),
            summary: string_field_diff_from_schema(diff.summary),
            media_content: media_field_diff_from_schema(diff.media_content),
            geolocation: field_diff_from_schema(diff.geolocation),
            datetime_range: field_diff_from_schema(diff.datetime_range),
        }
    }
}
