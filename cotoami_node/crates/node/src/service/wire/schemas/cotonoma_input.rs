//! Dedicated schema for `CotonomaInput`.
//!
//! `CotonomaInput` carries borrowed text internally. This schema owns the name
//! field so the transport format is explicit and decoupled from borrowing.

use std::borrow::Cow;

use cotoami_db::{models::DateTimeRange, prelude::*};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CotonomaInputSchema {
    pub name: String,
    #[serde(default)]
    pub geolocation: Option<Geolocation>,
    #[serde(default)]
    pub datetime_range: Option<DateTimeRange>,
}

impl From<CotonomaInput<'static>> for CotonomaInputSchema {
    fn from(input: CotonomaInput<'static>) -> Self {
        Self {
            name: input.name.into_owned(),
            geolocation: input.geolocation,
            datetime_range: input.datetime_range,
        }
    }
}

impl From<CotonomaInputSchema> for CotonomaInput<'static> {
    fn from(input: CotonomaInputSchema) -> Self {
        Self {
            name: Cow::Owned(input.name),
            geolocation: input.geolocation,
            datetime_range: input.datetime_range,
        }
    }
}
