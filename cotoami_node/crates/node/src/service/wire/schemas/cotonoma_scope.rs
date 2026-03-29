//! Dedicated schema for `CotonomaScope`.
//!
//! This keeps the recursive/local/depth traversal options under explicit wire
//! control instead of relying on direct serialization of the internal enum.

use cotoami_db::prelude::*;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum CotonomaScopeSchema {
    Local,
    Recursive,
    Depth { depth: usize },
}

impl From<CotonomaScope> for CotonomaScopeSchema {
    fn from(scope: CotonomaScope) -> Self {
        match scope {
            CotonomaScope::Local => Self::Local,
            CotonomaScope::Recursive => Self::Recursive,
            CotonomaScope::Depth(depth) => Self::Depth { depth },
        }
    }
}

impl From<CotonomaScopeSchema> for CotonomaScope {
    fn from(scope: CotonomaScopeSchema) -> Self {
        match scope {
            CotonomaScopeSchema::Local => Self::Local,
            CotonomaScopeSchema::Recursive => Self::Recursive,
            CotonomaScopeSchema::Depth { depth } => Self::Depth(depth),
        }
    }
}
