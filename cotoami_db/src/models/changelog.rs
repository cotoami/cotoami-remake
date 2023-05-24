//! A changelog is a series of changes in a Cotoami database recorded
//! for state machine replication.
//!
//! When replicating a database to another node, that node must ensure to
//! apply the changelog entries in the serial number order.

use super::coto::{Coto, Cotonoma, Link};
use super::node::Node;
use super::Id;
use crate::schema::changelog;
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use diesel::backend::RawValue;
use diesel::deserialize::FromSql;
use diesel::expression::AsExpression;
use diesel::prelude::*;
use diesel::serialize::ToSql;
use diesel::sql_types::Text;
use diesel::sqlite::Sqlite;

/////////////////////////////////////////////////////////////////////////////
// changelog
/////////////////////////////////////////////////////////////////////////////

/// A row in `changelog` table
///
/// - A `ChangelogEntry` must not be updated once it's inserted, so it
///   shouldn't impl `AsChangeset`.
#[derive(
    Debug, Clone, PartialEq, Eq, Identifiable, Queryable, serde::Serialize, serde::Deserialize,
)]
#[diesel(table_name = changelog, primary_key(serial_number))]
pub struct ChangelogEntry {
    /// Serial number of a changelog entry based on SQLite ROWID
    pub serial_number: i64,

    /// Universally unique changelog ID
    pub uuid: Id<ChangelogEntry>,

    /// UUID of the parent node from which this change came
    ///
    /// `None` if it is a local change
    #[serde(skip_serializing, skip_deserializing)]
    pub parent_node_id: Option<Id<Node>>,

    /// Original serial number in the parent node
    ///
    /// `None` if it is a local change
    #[serde(skip_serializing, skip_deserializing)]
    pub parent_serial_number: Option<i64>,

    /// The content of this change
    pub change: Change,

    /// Registration date in this database
    pub inserted_at: NaiveDateTime,
}

impl ChangelogEntry {
    pub fn inserted_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.inserted_at)
    }

    pub fn as_import_from<'a>(&'a self, parent_node_id: &'a Id<Node>) -> NewChangelogEntry {
        NewChangelogEntry {
            uuid: self.uuid,
            parent_node_id: Some(parent_node_id),
            parent_serial_number: Some(self.serial_number),
            change: &self.change,
        }
    }
}

/// An `Insertable` changelog entry
#[derive(Insertable)]
#[diesel(table_name = changelog)]
pub struct NewChangelogEntry<'a> {
    uuid: Id<ChangelogEntry>,
    parent_node_id: Option<&'a Id<Node>>,
    parent_serial_number: Option<i64>,
    change: &'a Change,
}

/////////////////////////////////////////////////////////////////////////////
// Change
/////////////////////////////////////////////////////////////////////////////

/// A serializable form of an atomic change in a Cotoami database
#[derive(Debug, Clone, PartialEq, Eq, AsExpression, serde::Serialize, serde::Deserialize)]
#[diesel(sql_type = Text)]
pub enum Change {
    None,
    CreateCoto(Coto),
    DeleteCoto(Id<Coto>),
    UpdateCoto {
        uuid: Id<Coto>,
        content: Option<String>,
        summary: Option<String>,
        updated_at: NaiveDateTime,
    },
    CreateCotonoma(Coto, Cotonoma),
    RenameCotonoma {
        uuid: Id<Cotonoma>,
        name: String,
    },
    DeleteCotonoma(Id<Cotonoma>),
    CreateLink(Link),
    UpdateLink {
        uuid: Id<Link>,
        linking_phrase: Option<String>,
    },
    DeleteLink(Id<Link>),
}

impl Change {
    pub fn new_changelog_entry(&self) -> NewChangelogEntry {
        NewChangelogEntry {
            uuid: Id::generate(),
            parent_node_id: None,
            parent_serial_number: None,
            change: self,
        }
    }
}

impl ToSql<Text, Sqlite> for Change {
    fn to_sql<'b>(
        &'b self,
        out: &mut diesel::serialize::Output<'b, '_, Sqlite>,
    ) -> diesel::serialize::Result {
        let json_string = serde_json::to_string(&self)?;
        // https://diesel.rs/guides/migration_guide.html#changed-tosql-implementations
        out.set_value(json_string);
        Ok(diesel::serialize::IsNull::No)
    }
}

impl FromSql<Text, Sqlite> for Change {
    fn from_sql(bytes: RawValue<Sqlite>) -> diesel::deserialize::Result<Self> {
        let json_string = <String as FromSql<Text, Sqlite>>::from_sql(bytes)?;
        Ok(serde_json::from_str(&json_string)?)
    }
}

/////////////////////////////////////////////////////////////////////////////
// tests
/////////////////////////////////////////////////////////////////////////////

#[cfg(test)]
mod tests {
    use super::*;
    use anyhow::Result;
    use chrono::NaiveDateTime;
    use indoc::indoc;
    use std::str::FromStr;

    #[test]
    fn changelog_entry_as_json() -> Result<()> {
        let changelog_entry = ChangelogEntry {
            serial_number: 1,
            uuid: Id::from_str("00000000-0000-0000-0000-000000000001")?,
            parent_node_id: None,
            parent_serial_number: None,
            change: Change::None,
            inserted_at: NaiveDateTime::parse_from_str("2023-01-02 03:04:05", "%Y-%m-%d %H:%M:%S")?,
        };

        // serialize
        let json_string = serde_json::to_string_pretty(&changelog_entry).unwrap();
        assert_eq!(
            json_string,
            indoc! {r#"
            {
              "serial_number": 1,
              "uuid": "00000000-0000-0000-0000-000000000001",
              "change": "None",
              "inserted_at": "2023-01-02T03:04:05"
            }"#}
        );

        // deserialize
        let changelog_entry2: ChangelogEntry = serde_json::from_str(&json_string)?;
        assert_eq!(changelog_entry2, changelog_entry);

        Ok(())
    }
}
