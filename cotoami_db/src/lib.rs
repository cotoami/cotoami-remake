//! Cotoami database based on [SQLite](https://sqlite.org/) and [Diesel](https://diesel.rs/)

pub mod prelude {
    pub use super::{
        db::{error::*, ops::Paginated, Database, DatabaseSession, Operator},
        models::{
            changelog::{Change, ChangelogEntry},
            coto::{Coto, Cotonoma},
            node::{local::LocalNode, Node, Principal},
            Id,
        },
    };
}

use base64::Engine;
use chrono::{offset::Utc, NaiveDateTime};
use rand::{distributions::Alphanumeric, thread_rng, Rng};
use serde::{Deserialize, Deserializer, Serializer};

pub mod db;
pub mod models;
mod schema;

/// Returns the current datetime in UTC.
fn current_datetime() -> NaiveDateTime { Utc::now().naive_utc() }

fn generate_session_token() -> String {
    // https://rust-lang-nursery.github.io/rust-cookbook/algorithms/randomness.html#create-random-passwords-from-a-set-of-alphanumeric-characters
    thread_rng()
        .sample_iter(&Alphanumeric)
        .take(32)
        .map(char::from)
        .collect()
}

/// Base64 serialization in serde
///
/// Meant to be used in `serialize_with`
/// https://serde.rs/field-attrs.html#serialize_with
fn as_base64<T, S>(bytes: &T, serializer: S) -> Result<S::Ok, S::Error>
where
    T: AsRef<[u8]>,
    S: Serializer,
{
    serializer.serialize_str(&base64::engine::general_purpose::STANDARD_NO_PAD.encode(bytes))
}

/// Base64 deserialization in serde
///
/// Meant to be used in `deserialize_with`
/// https://serde.rs/field-attrs.html#deserialize_with
fn from_base64<'de, D>(deserializer: D) -> Result<Vec<u8>, D::Error>
where
    D: Deserializer<'de>,
{
    <&str>::deserialize(deserializer).and_then(|s| {
        base64::engine::general_purpose::STANDARD_NO_PAD
            .decode(s)
            .map_err(|e| serde::de::Error::custom(format!("Invalid base64 string: {}, {}", s, e)))
    })
}
