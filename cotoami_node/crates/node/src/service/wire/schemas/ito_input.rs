//! Dedicated schema for `ItoInput`.
//!
//! `ItoInput` uses borrowed strings internally. This schema keeps the wire
//! representation fully owned and stable.

use std::borrow::Cow;

use cotoami_db::prelude::*;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ItoInputSchema {
    pub source_coto_id: Id<Coto>,
    pub target_coto_id: Id<Coto>,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub details: Option<String>,
    #[serde(default)]
    pub order: Option<i32>,
}

impl From<ItoInput<'static>> for ItoInputSchema {
    fn from(input: ItoInput<'static>) -> Self {
        Self {
            source_coto_id: input.source_coto_id,
            target_coto_id: input.target_coto_id,
            description: input.description.map(Cow::into_owned),
            details: input.details.map(Cow::into_owned),
            order: input.order,
        }
    }
}

impl From<ItoInputSchema> for ItoInput<'static> {
    fn from(input: ItoInputSchema) -> Self {
        Self {
            source_coto_id: input.source_coto_id,
            target_coto_id: input.target_coto_id,
            description: input.description.map(Cow::Owned),
            details: input.details.map(Cow::Owned),
            order: input.order,
        }
    }
}
