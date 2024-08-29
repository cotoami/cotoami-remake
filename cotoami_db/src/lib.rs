//! Cotoami database based on [SQLite](https://sqlite.org/) and [Diesel](https://diesel.rs/)

use base64::Engine;
use chrono::{offset::Utc, NaiveDateTime};
use rand::Rng;
use serde::{Deserialize, Deserializer, Serializer};

pub mod db;
pub mod models;
mod schema;

pub mod prelude {
    pub use crate::{
        db::{
            error::*,
            ops::{node_role_ops::*, Paginated},
            transactions::DatabaseSession,
            Database,
        },
        models::prelude::*,
    };
}
pub use crate::prelude::*;

/// Returns the current datetime in UTC.
fn current_datetime() -> NaiveDateTime { Utc::now().naive_utc() }

/// Generate a secret string of the given length which is 32 by default.
pub fn generate_secret(length: Option<usize>) -> String {
    let length = length.unwrap_or(32);

    // // https://rust-lang-nursery.github.io/rust-cookbook/algorithms/randomness.html#create-random-passwords-from-a-set-of-user-defined-characters
    let mut rng = rand::thread_rng();
    (0..length)
        .map(|_| {
            let index = rng.gen_range(0..SECRET_CHARSET.len());
            SECRET_CHARSET[index] as char
        })
        .collect()
}

const SECRET_CHARSET: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZ\
                            abcdefghijklmnopqrstuvwxyz\
                            0123456789)(*&^%$#@!~";

/// Support "double option" pattern in serde deserialization.
///
/// https://github.com/serde-rs/serde/issues/984#issuecomment-314143738
/// https://github.com/serde-rs/serde/issues/1042
///
/// rmp_serde does not support this pattern as of 1.3.0.
pub fn double_option<'de, T, D>(deserializer: D) -> Result<Option<Option<T>>, D::Error>
where
    T: Deserialize<'de>,
    D: Deserializer<'de>,
{
    Deserialize::deserialize(deserializer).map(Some)
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

fn blank_to_none(x: Option<&str>) -> Option<&str> {
    x.and_then(|x| if x.trim().is_empty() { None } else { Some(x) })
}

/// Abbreviate a string with the given `ellipsis` for testing display.
/// It doesn't consider grapheme clusters, but only unicode scalar values.
fn abbreviate_str(s: &str, length: usize, ellipsis: &str) -> Option<String> {
    if length == 0 {
        return Some(ellipsis.into());
    }
    match s.char_indices().nth(length) {
        Some((byte_end, _)) => {
            let abbreviated = &s[..byte_end];
            Some(format!("{abbreviated}{ellipsis}"))
        }
        None => None,
    }
}

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use googletest::prelude::*;

    use super::*;

    #[test]
    fn test_blank_to_none() -> Result<()> {
        assert_eq!(blank_to_none(Some("hello")), Some("hello"));
        assert_eq!(blank_to_none(Some(" hello ")), Some(" hello "));
        assert_eq!(blank_to_none(Some("")), None);
        assert_eq!(blank_to_none(Some("   ")), None);
        assert_eq!(blank_to_none(None), None);
        Ok(())
    }

    #[test]
    fn test_abbreviate_str() -> Result<()> {
        assert_eq!(abbreviate_str("ab", 0, "…"), Some("…".into()));
        assert_eq!(abbreviate_str("ab", 1, "…"), Some("a…".into()));
        assert_eq!(abbreviate_str("ab", 2, "…"), None);
        Ok(())
    }

    #[test]
    fn serde_double_option() -> Result<()> {
        #[derive(Debug, serde::Serialize, serde::Deserialize)]
        struct Test {
            #[serde(
                default,
                skip_serializing_if = "Option::is_none",
                deserialize_with = "double_option"
            )]
            foo: Option<Option<String>>,

            #[serde(
                default,
                skip_serializing_if = "Option::is_none",
                deserialize_with = "double_option"
            )]
            bar: Option<Option<String>>,

            #[serde(
                default,
                skip_serializing_if = "Option::is_none",
                deserialize_with = "double_option"
            )]
            baz: Option<Option<String>>,
        }

        let test = Test {
            foo: None,
            bar: Some(None),
            baz: Some(Some("hello".into())),
        };

        // via JSON
        let json = serde_json::to_string(&test)?;
        assert_eq!(json, "{\"bar\":null,\"baz\":\"hello\"}");
        let deserialized: Test = serde_json::from_str(&json)?;
        assert_that!(
            deserialized,
            matches_pattern!(Test {
                foo: none(),
                bar: some(none()),
                baz: some(some(eq("hello")))
            })
        );

        // via MessagePack (which does not support the "double option" pattern as of 1.3.0)

        // let msgpack = rmp_serde::to_vec(&test)?;
        // let deserialized: Test = rmp_serde::from_slice(&msgpack)?;
        // assert_that!(
        //     deserialized,
        //     matches_pattern!(Test {
        //         foo: none(),
        //         bar: some(none()),
        //         baz: some(some(eq("hello")))
        //     })
        // );

        Ok(())
    }
}
