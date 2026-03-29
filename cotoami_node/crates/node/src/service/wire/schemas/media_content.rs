//! Dedicated schema for media payloads carried by wire-level coto inputs/diffs.
//!
//! This schema exists so the transport format is expressed in terms of owned,
//! explicit fields instead of reusing internal tuple-style representations.

use cotoami_db::prelude::*;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MediaContentSchema {
    pub content: Bytes,
    pub media_type: String,
}
