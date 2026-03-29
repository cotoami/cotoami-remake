//! Dedicated schema for `ItoContentDiff`.
//!
//! The internal diff carries borrowed strings; this schema makes the wire
//! payload owned and explicitly governed by the protocol layer.

use cotoami_db::prelude::*;
use serde::{Deserialize, Serialize};

use super::{
    field_diff::{string_field_diff_from_schema, string_field_diff_to_schema},
    FieldDiffSchema,
};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ItoContentDiffSchema {
    pub description: FieldDiffSchema<String>,
    pub details: FieldDiffSchema<String>,
}

impl From<ItoContentDiff<'static>> for ItoContentDiffSchema {
    fn from(diff: ItoContentDiff<'static>) -> Self {
        Self {
            description: string_field_diff_to_schema(diff.description),
            details: string_field_diff_to_schema(diff.details),
        }
    }
}

impl From<ItoContentDiffSchema> for ItoContentDiff<'static> {
    fn from(diff: ItoContentDiffSchema) -> Self {
        Self {
            description: string_field_diff_from_schema(diff.description),
            details: string_field_diff_from_schema(diff.details),
        }
    }
}
