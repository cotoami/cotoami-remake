//! Dedicated schema for field diffs used by wire-level update payloads.
//!
//! The internal `FieldDiff<T>` is already explicit, but this schema keeps all
//! wire-visible diff shapes under the transport layer and provides helpers for
//! converting borrowed/internal forms into owned wire forms.

use std::borrow::Cow;

use cotoami_db::prelude::*;
use serde::{Deserialize, Serialize};

use super::MediaContentSchema;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum FieldDiffSchema<T> {
    None,
    Delete,
    Change { value: T },
}

pub(crate) fn field_diff_to_schema<T>(diff: FieldDiff<T>) -> FieldDiffSchema<T> {
    match diff {
        FieldDiff::None => FieldDiffSchema::None,
        FieldDiff::Delete => FieldDiffSchema::Delete,
        FieldDiff::Change(value) => FieldDiffSchema::Change { value },
    }
}

pub(crate) fn field_diff_from_schema<T>(diff: FieldDiffSchema<T>) -> FieldDiff<T> {
    match diff {
        FieldDiffSchema::None => FieldDiff::None,
        FieldDiffSchema::Delete => FieldDiff::Delete,
        FieldDiffSchema::Change { value } => FieldDiff::Change(value),
    }
}

pub(crate) fn string_field_diff_to_schema(
    diff: FieldDiff<Cow<'static, str>>,
) -> FieldDiffSchema<String> {
    match diff {
        FieldDiff::None => FieldDiffSchema::None,
        FieldDiff::Delete => FieldDiffSchema::Delete,
        FieldDiff::Change(value) => FieldDiffSchema::Change {
            value: value.into_owned(),
        },
    }
}

pub(crate) fn string_field_diff_from_schema(
    diff: FieldDiffSchema<String>,
) -> FieldDiff<Cow<'static, str>> {
    match diff {
        FieldDiffSchema::None => FieldDiff::None,
        FieldDiffSchema::Delete => FieldDiff::Delete,
        FieldDiffSchema::Change { value } => FieldDiff::Change(Cow::Owned(value)),
    }
}

pub(crate) fn media_field_diff_to_schema(
    diff: FieldDiff<(Bytes, Cow<'static, str>)>,
) -> FieldDiffSchema<MediaContentSchema> {
    match diff {
        FieldDiff::None => FieldDiffSchema::None,
        FieldDiff::Delete => FieldDiffSchema::Delete,
        FieldDiff::Change((content, media_type)) => FieldDiffSchema::Change {
            value: MediaContentSchema {
                content,
                media_type: media_type.into_owned(),
            },
        },
    }
}

pub(crate) fn media_field_diff_from_schema(
    diff: FieldDiffSchema<MediaContentSchema>,
) -> FieldDiff<(Bytes, Cow<'static, str>)> {
    match diff {
        FieldDiffSchema::None => FieldDiff::None,
        FieldDiffSchema::Delete => FieldDiff::Delete,
        FieldDiffSchema::Change { value } => {
            FieldDiff::Change((value.content, Cow::Owned(value.media_type)))
        }
    }
}
