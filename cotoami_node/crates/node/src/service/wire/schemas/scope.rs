//! Dedicated schema for `Scope`.
//!
//! `Scope` is an enum that participates directly in command payloads, so the
//! transport layer defines its own tagged schema to make the wire shape
//! deliberate and stable.

use cotoami_db::prelude::*;
use serde::{Deserialize, Serialize};

use super::CotonomaScopeSchema;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ScopeSchema {
    All,
    Node { node_id: Id<Node> },
    Cotonoma {
        cotonoma_id: Id<Cotonoma>,
        scope: CotonomaScopeSchema,
    },
}

impl From<Scope> for ScopeSchema {
    fn from(scope: Scope) -> Self {
        match scope {
            Scope::All => Self::All,
            Scope::Node(node_id) => Self::Node { node_id },
            Scope::Cotonoma((cotonoma_id, scope)) => Self::Cotonoma {
                cotonoma_id,
                scope: scope.into(),
            },
        }
    }
}

impl From<ScopeSchema> for Scope {
    fn from(scope: ScopeSchema) -> Self {
        match scope {
            ScopeSchema::All => Self::All,
            ScopeSchema::Node { node_id } => Self::Node(node_id),
            ScopeSchema::Cotonoma { cotonoma_id, scope } => {
                Self::Cotonoma((cotonoma_id, scope.into()))
            }
        }
    }
}
