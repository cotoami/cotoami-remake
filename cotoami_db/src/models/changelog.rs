//! A changelog is a series of changes in a Cotoami database recorded
//! for state machine replication.
//!
//! When replicating a database to another node, that node must ensure to
//! apply the changelog entries in the serial number order.

use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use diesel::{
    backend::Backend, deserialize::FromSql, expression::AsExpression, prelude::*, serialize::ToSql,
    sql_types::Binary, sqlite::Sqlite, FromSqlRow,
};

use super::{coto::Coto, cotonoma::Cotonoma, link::Link, node::Node, Id};
use crate::schema::changelog;

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
#[must_use]
pub struct ChangelogEntry {
    /// Serial number of a changelog entry based on SQLite ROWID
    pub serial_number: i64,

    /// UUID of the node in which this change has been originally created
    pub origin_node_id: Id<Node>,

    /// Serial number among changes created in the origin node
    pub origin_serial_number: i64,

    /// The content of this change
    pub change: Change,

    /// Registration date in this database
    pub inserted_at: NaiveDateTime,
}

impl ChangelogEntry {
    pub fn inserted_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.inserted_at) }

    pub fn to_import(&self) -> NewChangelogEntry {
        NewChangelogEntry {
            origin_node_id: &self.origin_node_id,
            origin_serial_number: self.origin_serial_number,
            change: &self.change,
            inserted_at: crate::current_datetime(),
        }
    }
}

/// An `Insertable` changelog entry
#[derive(Insertable)]
#[diesel(table_name = changelog)]
pub struct NewChangelogEntry<'a> {
    origin_node_id: &'a Id<Node>,
    origin_serial_number: i64,
    change: &'a Change,
    inserted_at: NaiveDateTime,
}

/////////////////////////////////////////////////////////////////////////////
// Change
/////////////////////////////////////////////////////////////////////////////

/// A serializable form of an atomic change in a Cotoami database
///
/// The variants are defined in terms of data change that can be shared with other nodes,
/// so they do not necessarily match the operations in the user-facing API.
#[derive(
    Debug, Clone, PartialEq, Eq, AsExpression, FromSqlRow, serde::Serialize, serde::Deserialize,
)]
#[diesel(sql_type = Binary)]
pub enum Change {
    None,
    CreateNode(Node, Option<(Cotonoma, Coto)>),
    UpsertNode(Node),
    RenameNode {
        uuid: Id<Node>,
        name: String,
        updated_at: NaiveDateTime,
    },
    SetRootCotonoma {
        uuid: Id<Node>,
        cotonoma_id: Id<Cotonoma>,
    },
    CreateCoto(Coto),
    EditCoto {
        uuid: Id<Coto>,
        content: String,
        summary: Option<String>,
        updated_at: NaiveDateTime,
    },
    DeleteCoto(Id<Coto>),
    CreateCotonoma(Cotonoma, Coto),
    RenameCotonoma {
        uuid: Id<Cotonoma>,
        name: String,
        updated_at: NaiveDateTime,
    },
    DeleteCotonoma(Id<Cotonoma>),
    CreateLink(Link),
    EditLink {
        uuid: Id<Link>,
        linking_phrase: Option<String>,
        updated_at: NaiveDateTime,
    },
    DeleteLink(Id<Link>),
}

impl Change {
    pub fn new_changelog_entry<'a>(
        &'a self,
        local_node_id: &'a Id<Node>,
        serial_number: i64,
    ) -> NewChangelogEntry {
        NewChangelogEntry {
            origin_node_id: local_node_id,
            origin_serial_number: serial_number,
            change: self,
            inserted_at: crate::current_datetime(),
        }
    }
}

impl ToSql<Binary, Sqlite> for Change {
    fn to_sql<'b>(
        &'b self,
        out: &mut diesel::serialize::Output<'b, '_, Sqlite>,
    ) -> diesel::serialize::Result {
        let msgpack_bytes = rmp_serde::to_vec(&self)?;
        // https://diesel.rs/guides/migration_guide.html#changed-tosql-implementations
        out.set_value(msgpack_bytes);
        Ok(diesel::serialize::IsNull::No)
    }
}

impl FromSql<Binary, Sqlite> for Change {
    fn from_sql(value: <Sqlite as Backend>::RawValue<'_>) -> diesel::deserialize::Result<Self> {
        let msgpack_bytes = <Vec<u8> as FromSql<Binary, Sqlite>>::from_sql(value)?;
        Ok(rmp_serde::from_slice(&msgpack_bytes)?)
    }
}

/////////////////////////////////////////////////////////////////////////////
// tests
/////////////////////////////////////////////////////////////////////////////

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use anyhow::Result;
    use chrono::NaiveDateTime;
    use indoc::indoc;

    use super::*;

    #[test]
    fn changelog_entry_as_json() -> Result<()> {
        let changelog_entry = ChangelogEntry {
            serial_number: 1,
            origin_node_id: Id::from_str("00000000-0000-0000-0000-000000000001")?,
            origin_serial_number: 1,
            change: Change::DeleteCoto(Id::from_str("00000000-0000-0000-0000-000000000001")?),
            inserted_at: NaiveDateTime::parse_from_str("2023-01-02 03:04:05", "%Y-%m-%d %H:%M:%S")?,
        };

        // serialize
        let json_string = serde_json::to_string_pretty(&changelog_entry).unwrap();
        assert_eq!(
            json_string,
            indoc! {r#"
            {
              "serial_number": 1,
              "origin_node_id": "00000000-0000-0000-0000-000000000001",
              "origin_serial_number": 1,
              "change": {
                "DeleteCoto": "00000000-0000-0000-0000-000000000001"
              },
              "inserted_at": "2023-01-02T03:04:05"
            }"#}
        );

        // deserialize
        let deserialized: ChangelogEntry = serde_json::from_str(&json_string)?;
        assert_eq!(deserialized, changelog_entry);

        Ok(())
    }

    #[test]
    fn message_pack_serialization() -> Result<()> {
        let change = Change::DeleteCotonoma(Id::from_str("00000000-0000-0000-0000-000000000001")?);
        let msgpack_bytes = rmp_serde::to_vec(&change)?;
        let deserialized: Change = rmp_serde::from_slice(&msgpack_bytes)?;
        assert_eq!(deserialized, change);
        Ok(())
    }
}
