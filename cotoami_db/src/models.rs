use derive_new::new;
use diesel::backend::RawValue;
use diesel::deserialize::{FromSql, FromSqlRow};
use diesel::expression::AsExpression;
use diesel::serialize::ToSql;
use diesel::sql_types::Text;
use diesel::sqlite::Sqlite;
use std::fmt::{Debug, Display};
use std::marker::PhantomData;
use std::str::FromStr;
use uuid::Uuid;

pub mod coto;
pub mod node;

/// A generic entity ID
#[derive(
    Debug,
    Clone,
    Copy,
    FromSqlRow,
    AsExpression,
    Hash,
    Eq,
    PartialEq,
    serde::Serialize,
    serde::Deserialize,
    new,
)]
#[diesel(sql_type = Text)]
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

impl<T> FromStr for Id<T> {
    type Err = uuid::Error;
    fn from_str(uuid: &str) -> Result<Self, Self::Err> {
        Ok(Id::new(Uuid::from_str(uuid)?))
    }
}

impl<T: Debug> ToSql<Text, Sqlite> for Id<T> {
    fn to_sql<'b>(
        &'b self,
        out: &mut diesel::serialize::Output<'b, '_, Sqlite>,
    ) -> diesel::serialize::Result {
        let string = self.as_uuid().as_hyphenated().to_string();
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
