//! Data structure that represents a Cotoami database

use derive_new::new;
use diesel::backend::RawValue;
use diesel::deserialize::FromSql;
use diesel::expression::AsExpression;
use diesel::serialize::ToSql;
use diesel::sql_types::Text;
use diesel::sqlite::Sqlite;
use std::fmt::{Debug, Display};
use std::marker::PhantomData;
use std::str::FromStr;
use uuid::Uuid;

pub mod changelog;
pub mod coto;
pub mod node;

/////////////////////////////////////////////////////////////////////////////
// Id<T>
/////////////////////////////////////////////////////////////////////////////

/// A generic entity ID
#[derive(Debug, Clone, Copy, AsExpression, Hash, Eq, PartialEq, serde::Deserialize, new)]
#[diesel(sql_type = Text)]
#[serde(try_from = "&str")]
pub struct Id<T> {
    value: Uuid,

    // `fn() -> T`
    // has the same variance as T
    // but not to own data of type T
    // unlike *const T, it implements both Send and Sync
    _marker: PhantomData<fn() -> T>,
}

impl<T> Id<T> {
    pub fn generate() -> Self {
        Id::new(Uuid::new_v4())
    }

    pub fn as_uuid(&self) -> Uuid {
        self.value
    }
}

impl<T> Display for Id<T> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.as_uuid().as_hyphenated())
    }
}

impl<T> serde::Serialize for Id<T> {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: serde::Serializer,
    {
        self.as_uuid().serialize(serializer)
    }
}

/// Convert: &str -> Id<T> or error
impl<T> FromStr for Id<T> {
    type Err = uuid::Error;
    fn from_str(uuid: &str) -> Result<Self, Self::Err> {
        Ok(Id::new(Uuid::from_str(uuid)?))
    }
}

/// Convert: &str -> Id<T> or error
impl<T> TryFrom<&str> for Id<T> {
    type Error = uuid::Error;

    fn try_from(value: &str) -> Result<Self, Self::Error> {
        Self::from_str(value)
    }
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
    fn from_sql(bytes: RawValue<Sqlite>) -> diesel::deserialize::Result<Self> {
        let string = <String as FromSql<Text, Sqlite>>::from_sql(bytes)?;
        Uuid::parse_str(&string)
            .map(Self::new)
            .map_err(|e| e.into())
    }
}

/////////////////////////////////////////////////////////////////////////////
// Ids<T>
/////////////////////////////////////////////////////////////////////////////

/// A list of entity IDs stored as a comma-separated text in a database
#[derive(Debug, Clone, AsExpression, Hash, Eq, PartialEq, serde::Serialize, serde::Deserialize)]
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
    fn from_sql(bytes: RawValue<Sqlite>) -> diesel::deserialize::Result<Self> {
        let raw_value = <String as FromSql<Text, Sqlite>>::from_sql(bytes)?;
        let str_ids = raw_value.split(',');
        let mut ids: Vec<Id<T>> = Vec::new();
        for str_id in str_ids {
            ids.push(str_id.to_string().parse()?);
        }
        Ok(Self(ids))
    }
}
