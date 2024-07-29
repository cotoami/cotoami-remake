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

use super::{coto::Coto, cotonoma::Cotonoma, link::Link, node::Node, Bytes, Id};
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

    /// Number to distinguish between different change types
    ///
    /// The source type is `u8` (Change::type_number()), but we had to pick `i16` instead
    /// because there is no sqlite type to represent `u8`.
    pub type_number: i16,

    /// The content of this change
    pub change: Change,

    /// Registration date in this database
    pub inserted_at: NaiveDateTime,
}

impl ChangelogEntry {
    pub fn inserted_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.inserted_at) }

    pub(crate) fn to_import(&self) -> NewChangelogEntry {
        NewChangelogEntry {
            origin_node_id: &self.origin_node_id,
            origin_serial_number: self.origin_serial_number,
            type_number: self.type_number,
            change: &self.change,
            inserted_at: crate::current_datetime(),
        }
    }
}

/// An `Insertable` changelog entry
#[derive(Insertable)]
#[diesel(table_name = changelog)]
pub(crate) struct NewChangelogEntry<'a> {
    origin_node_id: &'a Id<Node>,
    origin_serial_number: i64,
    type_number: i16,
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
#[repr(u8)]
pub enum Change {
    None = 0,
    CreateNode {
        node: Node,
        root: Option<(Cotonoma, Coto)>,
    } = 1,
    UpsertNode(Node) = 2,
    RenameNode {
        node_id: Id<Node>,
        name: String,
        updated_at: NaiveDateTime,
    } = 3,
    SetNodeIcon {
        node_id: Id<Node>,
        icon: Bytes,
    } = 4,
    SetRootCotonoma {
        node_id: Id<Node>,
        cotonoma_id: Id<Cotonoma>,
    } = 5,
    CreateCoto(Coto) = 6,
    EditCoto {
        coto_id: Id<Coto>,
        content: String,
        summary: Option<String>,
        updated_at: NaiveDateTime,
    } = 7,
    // Used to delete a coto or cotonoma
    DeleteCoto {
        coto_id: Id<Coto>,
        deleted_at: NaiveDateTime,
    } = 8,
    CreateCotonoma(Cotonoma, Coto) = 9,
    RenameCotonoma {
        cotonoma_id: Id<Cotonoma>,
        name: String,
        updated_at: NaiveDateTime,
    } = 10,
    CreateLink(Link) = 11,
    EditLink {
        link_id: Id<Link>,
        linking_phrase: Option<String>,
        details: Option<String>,
        updated_at: NaiveDateTime,
    } = 12,
    DeleteLink(Id<Link>) = 13,
    ChangeOwnerNode {
        from: Id<Node>,
        to: Id<Node>,
        // When applying this change, the last number of the changelog entry of
        // the `from` node must match the this number. Normally it's not possible that
        // this value is larger than the actual last number at the time of applying this change,
        // but if the value is smaller than the actual number, which means there are changes
        // unknown to the `to` node, new changes in the `to` node will possibly cause conflicts
        // with the unknown changes.
        last_change_number: i64,
    } = 14,
}

impl Change {
    pub(crate) fn new_changelog_entry<'a>(
        &'a self,
        local_node_id: &'a Id<Node>,
        serial_number: i64,
    ) -> NewChangelogEntry {
        NewChangelogEntry {
            origin_node_id: local_node_id,
            origin_serial_number: serial_number,
            type_number: self.type_number() as i16,
            change: self,
            inserted_at: crate::current_datetime(),
        }
    }

    pub fn type_number(&self) -> u8 {
        // There seems to be no "safe" way to get a discriminant value of an enum with fields
        // other than the nightly-only experimental `core::intrinsics::discriminant_value`.
        //
        // "Rust provides no language-level way to access the raw discriminant of an enum with fields.
        // Instead, currently unsafe code must be used to inspect the discriminant of an enum with fields.
        // Since this feature is intended for use with cross-language FFI where unsafe code is already
        // necessary, this should hopefully not be too much of an extra burden."
        // https://blog.rust-lang.org/2022/12/15/Rust-1.66.0.html#explicit-discriminants-on-enums-with-fields
        //
        // Pointer casting:
        // https://doc.rust-lang.org/reference/items/enumerations.html#pointer-casting
        unsafe { *(self as *const Self as *const u8) }
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
        let change = Change::None;
        let changelog_entry = ChangelogEntry {
            serial_number: 1,
            origin_node_id: Id::from_str("00000000-0000-0000-0000-000000000001")?,
            origin_serial_number: 1,
            type_number: change.type_number() as i16,
            change,
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
              "type_number": 0,
              "change": "None",
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
        let change = Change::DeleteLink(Id::from_str("00000000-0000-0000-0000-000000000001")?);
        let msgpack_bytes = rmp_serde::to_vec(&change)?;
        let deserialized: Change = rmp_serde::from_slice(&msgpack_bytes)?;
        assert_eq!(deserialized, change);
        Ok(())
    }
}
