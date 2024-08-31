//! Data structure that represents a Cotoami database

use std::{
    cmp::Ordering,
    convert::AsRef,
    fmt,
    fmt::{Debug, Display},
    hash::{Hash, Hasher},
    io::Cursor,
    marker::PhantomData,
    str::FromStr,
};

use anyhow::Result;
use derive_new::new;
use diesel::{
    backend::Backend,
    deserialize::FromSql,
    expression::AsExpression,
    serialize::ToSql,
    sql_types::{Binary, Text},
    sqlite::Sqlite,
    FromSqlRow,
};
use image::{imageops::FilterType, ImageFormat};
use serde::{de, ser, Deserializer};
use tracing::debug;
use uuid::Uuid;
use validator::Validate;

use self::{
    node::{parent::ParentNode, Node},
    operator::Operator,
};

pub mod changelog;
pub mod coto;
pub mod cotonoma;
pub mod graph;
pub mod link;
pub mod node;
pub mod operator;

pub(crate) mod prelude {
    pub use super::{
        changelog::*,
        coto::*,
        cotonoma::*,
        graph::*,
        link::*,
        node::{child::*, client::*, local::*, parent::*, roles::*, server::*, *},
        operator::*,
        Bytes, ClientSession, FieldDiff, Geolocation, Id, Ids,
    };
}

/////////////////////////////////////////////////////////////////////////////
// Id<T>
/////////////////////////////////////////////////////////////////////////////

/// A generic entity ID
#[derive(
    derive_more::Debug, AsExpression, FromSqlRow, serde::Serialize, serde::Deserialize, new,
)]
#[diesel(sql_type = Text)]
#[serde(transparent)]
pub struct Id<T> {
    value: Uuid,

    // `fn() -> T`
    // has the same variance as T
    // but not to own data of type T
    // unlike *const T, it implements both Send and Sync
    #[debug(skip)]
    _marker: PhantomData<fn() -> T>,
}

impl<T> Id<T> {
    /// Generates a version 7 UUID as a new ID.
    ///
    /// As UUIDv7 is time-ordered, values generated are practically sequential
    /// and therefore eliminates the index locality problem.
    /// The time-ordered nature of UUIDv7 results in much better DB performance
    /// compared to random-prefixed UUIDv4s.
    pub fn generate() -> Self { Id::new(Uuid::now_v7()) }

    pub fn as_uuid(&self) -> Uuid { self.value }
}

// Manually implement PartialEq/Eq to avoid incorrect bounds on T.
// https://github.com/rust-lang/rust/issues/26925
impl<T> PartialEq for Id<T> {
    fn eq(&self, other: &Self) -> bool { self.value == other.value }
}
impl<T> Eq for Id<T> {}

impl<T> Display for Id<T> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.as_uuid().as_hyphenated())
    }
}

impl<T> From<Id<T>> for String {
    fn from(id: Id<T>) -> Self { id.to_string() }
}

impl<T> FromStr for Id<T> {
    type Err = uuid::Error;
    fn from_str(uuid: &str) -> Result<Self, Self::Err> { Ok(Id::new(Uuid::from_str(uuid)?)) }
}

impl<T> TryFrom<&str> for Id<T> {
    type Error = uuid::Error;

    fn try_from(value: &str) -> Result<Self, Self::Error> { Self::from_str(value) }
}

impl<T: Debug> ToSql<Text, Sqlite> for Id<T> {
    fn to_sql<'a>(
        &'a self,
        out: &mut diesel::serialize::Output<'a, '_, Sqlite>,
    ) -> diesel::serialize::Result {
        let string = self.as_uuid().as_hyphenated().to_string();
        // https://diesel.rs/guides/migration_guide.html#changed-tosql-implementations
        out.set_value(string);
        Ok(diesel::serialize::IsNull::No)
    }
}

impl<T> FromSql<Text, Sqlite> for Id<T> {
    fn from_sql(bytes: <Sqlite as Backend>::RawValue<'_>) -> diesel::deserialize::Result<Self> {
        let string = <String as FromSql<Text, Sqlite>>::from_sql(bytes)?;
        Uuid::parse_str(&string)
            .map(Self::new)
            .map_err(|e| e.into())
    }
}

//
// The following impls can't be automatically derived due to the limitation of #[derive]
// https://github.com/rust-lang/rust/issues/26925
//

impl<T> Copy for Id<T> {}

impl<T> Clone for Id<T> {
    fn clone(&self) -> Self { *self }
}

impl<T: Eq> Ord for Id<T> {
    fn cmp(&self, other: &Self) -> Ordering { self.value.cmp(&other.value) }
}

impl<T: Eq> PartialOrd for Id<T> {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> { Some(self.cmp(other)) }
}

impl<T> Hash for Id<T> {
    fn hash<H: Hasher>(&self, state: &mut H) { self.value.hash(state); }
}

/////////////////////////////////////////////////////////////////////////////
// Ids<T>
/////////////////////////////////////////////////////////////////////////////

/// A list of entity IDs stored as a comma-separated text in a database
#[derive(
    Debug, Clone, PartialEq, Eq, AsExpression, FromSqlRow, serde::Serialize, serde::Deserialize,
)]
#[diesel(sql_type = Text)]
pub struct Ids<T>(pub Vec<Id<T>>);

impl<T: Debug> ToSql<Text, Sqlite> for Ids<T> {
    fn to_sql<'a>(
        &'a self,
        out: &mut diesel::serialize::Output<'a, '_, Sqlite>,
    ) -> diesel::serialize::Result {
        let string = self
            .0
            .iter()
            .map(|id| id.as_uuid().as_hyphenated().to_string())
            .collect::<Vec<_>>()
            .join(",");
        // https://diesel.rs/guides/migration_guide.html#changed-tosql-implementations
        out.set_value(string);
        Ok(diesel::serialize::IsNull::No)
    }
}

impl<T> FromSql<Text, Sqlite> for Ids<T> {
    fn from_sql(bytes: <Sqlite as Backend>::RawValue<'_>) -> diesel::deserialize::Result<Self> {
        let raw_value = <String as FromSql<Text, Sqlite>>::from_sql(bytes)?;
        let str_ids = raw_value.split(',');
        let mut ids: Vec<Id<T>> = Vec::new();
        for str_id in str_ids {
            ids.push(str_id.to_string().parse()?);
        }
        Ok(Self(ids))
    }
}

/////////////////////////////////////////////////////////////////////////////
// Bytes
/////////////////////////////////////////////////////////////////////////////

/// A binary type that can be stored to or restored from a database via Diesel.
///
/// This type supports switching serialize/deserialize format according to
/// the return values of [ser::Serializer::is_human_readable] and
/// [de::Deserializer::is_human_readable]. Therefore, it is also suited to
/// be in a data structure sent via network.
#[derive(Debug, Clone, PartialEq, Eq, AsExpression, FromSqlRow)]
#[diesel(sql_type = Binary)]
pub struct Bytes(bytes::Bytes);

impl Bytes {
    fn from_base64<'de, D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        crate::from_base64(deserializer).map(Bytes::from)
    }

    pub fn inner(&self) -> bytes::Bytes { self.0.clone() }
}

impl AsRef<[u8]> for Bytes {
    fn as_ref(&self) -> &[u8] { self.0.as_ref() }
}

impl From<Vec<u8>> for Bytes {
    fn from(vec: Vec<u8>) -> Bytes { Bytes(bytes::Bytes::from(vec)) }
}

impl From<bytes::Bytes> for Bytes {
    fn from(bytes: bytes::Bytes) -> Bytes { Bytes(bytes) }
}

impl From<Bytes> for bytes::Bytes {
    fn from(bytes: Bytes) -> bytes::Bytes { bytes.0 }
}

impl ser::Serialize for Bytes {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: ser::Serializer,
    {
        if serializer.is_human_readable() {
            crate::as_base64(self, serializer)
        } else {
            serializer.serialize_bytes(self.as_ref())
        }
    }
}

impl<'de> de::Deserialize<'de> for Bytes {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: de::Deserializer<'de>,
    {
        struct BytesVisitor;
        impl<'de> de::Visitor<'de> for BytesVisitor {
            type Value = Bytes;

            fn expecting(&self, f: &mut fmt::Formatter) -> fmt::Result {
                f.write_str("a byte array")
            }

            fn visit_bytes<E>(self, v: &[u8]) -> Result<Self::Value, E>
            where
                E: de::Error,
            {
                Ok(Bytes::from(v.to_vec()))
            }

            // It is never correct to implement `visit_byte_buf` without implementing `visit_bytes`.
            // Implement neither, both, or just visit_bytes.
            // https://docs.rs/serde/latest/serde/de/trait.Visitor.html#method.visit_byte_buf
            fn visit_byte_buf<E>(self, v: Vec<u8>) -> Result<Self::Value, E>
            where
                E: de::Error,
            {
                Ok(Bytes::from(v))
            }
        }

        if deserializer.is_human_readable() {
            Bytes::from_base64(deserializer)
        } else {
            // Deserialize implementations that benefit from taking ownership of `Vec<u8>` data
            // should indicate that to the deserializer by using `Deserializer::deserialize_byte_buf`
            // rather than `Deserializer::deserialize_bytes`, although not every deserializer will
            // honor such a request.
            deserializer.deserialize_byte_buf(BytesVisitor)
        }
    }
}

impl ToSql<Binary, Sqlite> for Bytes {
    fn to_sql<'b>(
        &'b self,
        out: &mut diesel::serialize::Output<'b, '_, Sqlite>,
    ) -> diesel::serialize::Result {
        // https://diesel.rs/guides/migration_guide.html#changed-tosql-implementations
        out.set_value(self.as_ref());
        Ok(diesel::serialize::IsNull::No)
    }
}

impl FromSql<Binary, Sqlite> for Bytes {
    fn from_sql(value: <Sqlite as Backend>::RawValue<'_>) -> diesel::deserialize::Result<Self> {
        let bytes = <Vec<u8> as FromSql<Binary, Sqlite>>::from_sql(value)?;
        Ok(Bytes(bytes::Bytes::from(bytes)))
    }
}

/////////////////////////////////////////////////////////////////////////////
// FieldDiff
/////////////////////////////////////////////////////////////////////////////

/// Serializable data structure to represent an update to a field of type `T` in a database.
///
/// This kind of data can be represented as a "double option" pattern in Rust. For
/// example, Diesel supports this pattern: https://diesel.rs/guides/all-about-updates.html
/// However, some binary serialization libraries do not support this pattern, because
/// they have to deal with "missing field", "null", "some value" correctly, and that seems to
/// be inherently difficult for some binary formats. Thus we define this struct as a more safe
/// type in terms of serialization.
#[derive(derive_more::Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize, Default)]
pub enum FieldDiff<T> {
    #[default]
    None,
    Delete,
    Change(T),
}

impl<T> FieldDiff<T> {
    pub const fn as_ref(&self) -> FieldDiff<&T> {
        match self {
            Self::None => FieldDiff::<&T>::None,
            Self::Delete => FieldDiff::<&T>::Delete,
            Self::Change(t) => FieldDiff::<&T>::Change(t),
        }
    }

    pub fn map_to_double_option<U, F>(self, f: F) -> Option<Option<U>>
    where
        F: FnOnce(T) -> U,
    {
        match self {
            Self::None => None,
            Self::Delete => Some(None),
            Self::Change(t) => Some(Some(f(t))),
        }
    }
}

impl<T> validator::Validate for FieldDiff<T>
where
    T: validator::Validate,
{
    fn validate(&self) -> Result<(), validator::ValidationErrors> {
        if let Self::Change(t) = self {
            T::validate(t)
        } else {
            Ok(())
        }
    }
}

impl<T> validator::ValidateLength<u64> for FieldDiff<T>
where
    T: validator::ValidateLength<u64>,
{
    fn length(&self) -> Option<u64> {
        if let Self::Change(t) = self {
            T::length(t)
        } else {
            None
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Geolocation
/////////////////////////////////////////////////////////////////////////////

#[derive(derive_more::Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize, Validate)]
pub struct Geolocation {
    #[validate(range(min = "Self::LONGITUDE_MIN", max = "Self::LONGITUDE_MAX"))]
    pub longitude: f64,

    #[validate(range(min = "Self::LATITUDE_MIN", max = "Self::LATITUDE_MAX"))]
    pub latitude: f64,
}

impl Geolocation {
    pub const LONGITUDE_MIN: f64 = -180.0;
    pub const LONGITUDE_MAX: f64 = 180.0;
    pub const LATITUDE_MIN: f64 = -90.0;
    pub const LATITUDE_MAX: f64 = 90.0;

    pub fn from_lng_lat(lng_lat: (f64, f64)) -> Self {
        Self {
            longitude: lng_lat.0,
            latitude: lng_lat.1,
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// ClientSession
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone)]
pub enum ClientSession {
    Operator(Operator),
    ParentNode(ParentNode),
}

impl ClientSession {
    pub fn client_node_id(&self) -> Id<Node> {
        match self {
            Self::Operator(Operator::Owner(local_node_id)) => *local_node_id,
            Self::Operator(Operator::ChildNode(child)) => child.node_id,
            Self::ParentNode(parent) => parent.node_id,
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Utilities
/////////////////////////////////////////////////////////////////////////////

fn resize_image(image: &[u8], max_size: u32, format: Option<ImageFormat>) -> Result<Vec<u8>> {
    let format = if let Some(format) = format {
        format
    } else {
        image::guess_format(image)?
    };
    let mut image = image::load_from_memory(image)?;

    // Resize the image if it is larger than the max_size.
    if image.width() > max_size || image.height() > max_size {
        debug!(
            "Resizing an image ({} * {}) to fit within the bounds ({max_size})",
            image.width(),
            image.height()
        );
        image = image.resize(max_size, max_size, FilterType::Lanczos3);
    }

    // Return the bytes of the resized image.
    let mut bytes: Vec<u8> = Vec::new();
    image.write_to(&mut Cursor::new(&mut bytes), format)?;
    Ok(bytes)
}

/////////////////////////////////////////////////////////////////////////////
// tests
/////////////////////////////////////////////////////////////////////////////

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use googletest::prelude::*;

    use super::*;

    #[derive(Debug, PartialEq, Eq)]
    struct Foo();

    #[test]
    fn id_json_serialization() -> Result<()> {
        let id: Id<Foo> = Id::from_str("00000000-0000-0000-0000-000000000001")?;

        let json_string = serde_json::to_string(&id)?;
        assert_that!(json_string, eq(r#""00000000-0000-0000-0000-000000000001""#));
        println!("Id json_string size: {}", json_string.as_bytes().len());

        let deserialized: Id<Foo> = serde_json::from_str(&json_string)?;
        assert_that!(deserialized, eq(id));
        Ok(())
    }

    #[test]
    fn id_message_pack_serialization() -> Result<()> {
        let id: Id<Foo> = Id::from_str("00000000-0000-0000-0000-000000000001")?;

        let msgpack_bytes = rmp_serde::to_vec(&id)?;
        println!("Id msgpack_bytes size: {}", msgpack_bytes.len());

        let deserialized: Id<Foo> = rmp_serde::from_slice(&msgpack_bytes)?;
        assert_that!(deserialized, eq(id));
        Ok(())
    }

    #[test]
    fn bytes_json_serialization() -> Result<()> {
        let bytes: Bytes = Bytes(bytes::Bytes::from("Hello world"));

        let json_string = serde_json::to_string(&bytes)?;
        println!("Bytes json_string size: {}", json_string.as_bytes().len());

        let deserialized: Bytes = serde_json::from_str(&json_string)?;
        assert_that!(deserialized, eq(&bytes));
        Ok(())
    }

    #[test]
    fn bytes_message_pack_serialization() -> Result<()> {
        let bytes: Bytes = Bytes(bytes::Bytes::from("Hello world"));

        let msgpack_bytes = rmp_serde::to_vec(&bytes)?;
        println!("Bytes msgpack_bytes size: {}", msgpack_bytes.len());

        let deserialized: Bytes = rmp_serde::from_slice(&msgpack_bytes)?;
        assert_that!(deserialized, eq(&bytes));
        Ok(())
    }
}
