//! Data structure that represents a Cotoami database

use std::{
    fmt::{Debug, Display},
    hash::{Hash, Hasher},
    marker::PhantomData,
    str::FromStr,
};

use derive_new::new;
use diesel::{
    backend::Backend, deserialize::FromSql, expression::AsExpression, serialize::ToSql,
    sql_types::Text, sqlite::Sqlite, FromSqlRow,
};
use uuid::Uuid;

use self::{node::parent::ParentNode, operator::Operator};

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
        node::{child::*, client::*, local::*, parent::*, server::*, *},
        operator::*,
        ClientSession, Id, Ids,
    };
}

/////////////////////////////////////////////////////////////////////////////
// Id<T>
/////////////////////////////////////////////////////////////////////////////

/// A generic entity ID
#[derive(
    derive_more::Debug,
    PartialEq,
    Eq,
    AsExpression,
    FromSqlRow,
    serde::Serialize,
    serde::Deserialize,
    new,
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
    pub fn generate() -> Self { Id::new(Uuid::new_v4()) }

    pub fn as_uuid(&self) -> Uuid { self.value }
}

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
pub struct Ids<T>(Vec<Id<T>>);

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
// ClientSession
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone)]
pub enum ClientSession {
    Operator(Operator),
    ParentNode(ParentNode),
}

/////////////////////////////////////////////////////////////////////////////
// tests
/////////////////////////////////////////////////////////////////////////////

#[cfg(test)]
mod tests {
    use anyhow::Result;

    use super::*;

    #[derive(Debug, PartialEq, Eq)]
    struct Foo();

    #[test]
    fn json_serialization() -> Result<()> {
        let id: Id<Foo> = Id::from_str("00000000-0000-0000-0000-000000000001")?;

        let json_string = serde_json::to_string(&id)?;
        assert_eq!(json_string, r#""00000000-0000-0000-0000-000000000001""#);
        println!("json_string size: {}", json_string.as_bytes().len());

        let deserialized: Id<Foo> = serde_json::from_str(&json_string)?;
        assert_eq!(deserialized, id);
        Ok(())
    }

    #[test]
    fn message_pack_serialization() -> Result<()> {
        let id: Id<Foo> = Id::from_str("00000000-0000-0000-0000-000000000001")?;
        let msgpack_bytes = rmp_serde::to_vec(&id)?;
        println!("msgpack_bytes size: {}", msgpack_bytes.len());
        let deserialized: Id<Foo> = rmp_serde::from_slice(&msgpack_bytes)?;
        assert_eq!(deserialized, id);
        Ok(())
    }
}
