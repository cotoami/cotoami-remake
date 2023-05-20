//! A node is a single Cotoami database that has connections to/from other databases(nodes).

use super::coto::Cotonoma;
use super::Id;
use crate::schema::{child_nodes, imported_nodes, nodes, parent_nodes};
use anyhow::{anyhow, Result};
use argon2::password_hash::rand_core::OsRng;
use argon2::password_hash::{PasswordHash, PasswordHasher, PasswordVerifier, SaltString};
use argon2::Argon2;
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use derive_new::new;
use diesel::prelude::*;
use identicon_rs::Identicon;

/////////////////////////////////////////////////////////////////////////////
// nodes
/////////////////////////////////////////////////////////////////////////////

/// A row in `nodes` table
#[derive(
    Debug,
    Clone,
    Eq,
    PartialEq,
    Identifiable,
    AsChangeset,
    Queryable,
    serde::Serialize,
    serde::Deserialize,
)]
#[diesel(primary_key(rowid))]
pub struct Node {
    /// SQLite rowid (so-called "integer primary key")
    #[serde(skip_serializing, skip_deserializing)]
    pub rowid: i64,

    /// Universally unique node ID
    pub uuid: Id<Node>,

    /// Icon image
    pub icon: Vec<u8>,

    /// Display name
    pub name: String,

    /// UUID of the root cotonoma of this node
    pub root_cotonoma_id: Option<Id<Cotonoma>>,

    /// Password for owner authentication of this node
    /// This value can be set only in "self node record (rowid = 1)".
    #[serde(skip_serializing, skip_deserializing)]
    pub owner_password_hash: Option<String>,

    /// Version of node info for synchronizing among databases
    pub version: i32,

    /// Creation date of this node
    pub created_at: NaiveDateTime,

    /// Registration date in this database
    pub inserted_at: NaiveDateTime,
}

impl Node {
    // rowid for "self node record"
    pub const ROWID_FOR_SELF: i64 = 1;

    pub fn created_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.created_at)
    }

    pub fn inserted_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.inserted_at)
    }

    pub fn update_owner_password<'a>(&mut self, password: &'a str) -> Result<()> {
        if self.rowid != Self::ROWID_FOR_SELF {
            return Err(anyhow!(
                "Owner password cannot be set to this node (rowid: {:?})",
                self.rowid
            ));
        }
        let password_hash = hash_password(password.as_bytes())?;
        self.owner_password_hash = Some(password_hash);
        Ok(())
    }

    pub fn verify_owner_password<'a>(&self, password: &'a str) -> Result<()> {
        let password_hash = self
            .owner_password_hash
            .as_ref()
            .ok_or(anyhow!("This node has no password assigned."))?;
        let parsed_hash = PasswordHash::new(&password_hash)?;
        Argon2::default().verify_password(password.as_bytes(), &parsed_hash)?;
        Ok(())
    }

    /// Converting a foreign node into an importable data.
    ///
    /// - It assumes the node data came from another node (parent node).
    /// - `owner_password_hash` will be `None` (it should not be sent in the first place).
    /// - `created_at` is the original date of the node creation, so it should be kept.
    pub fn to_import(self) -> NewNode {
        NewNode {
            rowid: None,
            uuid: self.uuid,
            icon: self.icon,
            name: self.name,
            root_cotonoma_id: self.root_cotonoma_id,
            owner_password_hash: None,
            version: self.version,
            created_at: Some(self.created_at),
        }
    }
}

/// An `Insertable` node data
///
/// - `uuid` and `icon` are owned values because they are generated in the `new_` constructors.
/// - To avoid cloning `icon` in `Node::to_import`, the function must be consuming the
///   self, which requires every field in this struct is an owned value.
/// - As a result of it, the constructors need to clone some fields, which we think
///   is trivial because they will be called only once at the first launch
///   while `Node::to_import` will be likely more frequent.
#[derive(Insertable)]
#[diesel(table_name = nodes)]
pub struct NewNode {
    rowid: Option<i64>,
    uuid: Id<Node>,
    icon: Vec<u8>,
    name: String,
    root_cotonoma_id: Option<Id<Cotonoma>>,
    owner_password_hash: Option<String>,
    version: i32,
    created_at: Option<NaiveDateTime>,
}

impl NewNode {
    /// Create a desktop node that represents **this** database.
    pub fn new_desktop<'a>(name: &'a str) -> Result<Self> {
        let uuid = Id::generate();
        let icon_binary = generate_identicon(&uuid.to_string())?;
        Ok(Self {
            rowid: Some(Node::ROWID_FOR_SELF),
            uuid,
            icon: icon_binary,
            name: name.to_string(),
            root_cotonoma_id: None,
            owner_password_hash: None,
            version: 1,
            created_at: None,
        })
    }

    /// Create a server node that represents **this** database.
    pub fn new_server<'a>(name: &'a str, password: &'a str) -> Result<Self> {
        let uuid = Id::generate();
        let icon_binary = generate_identicon(&uuid.to_string())?;
        let password_hash = hash_password(password.as_bytes())?;
        Ok(Self {
            rowid: Some(Node::ROWID_FOR_SELF),
            uuid,
            icon: icon_binary,
            name: name.to_string(),
            root_cotonoma_id: None,
            owner_password_hash: Some(password_hash),
            version: 1,
            created_at: None,
        })
    }
}

fn generate_identicon<'a>(name: &'a str) -> Result<Vec<u8>> {
    let icon_binary = Identicon::new(name).export_png_data()?;
    Ok(icon_binary)
}

fn hash_password(password: &[u8]) -> Result<String> {
    let salt = SaltString::generate(&mut OsRng);
    let argon2 = Argon2::default();
    let password_hash = argon2.hash_password(password, &salt)?.to_string();
    Ok(password_hash)
}

/////////////////////////////////////////////////////////////////////////////
// parent_nodes
/////////////////////////////////////////////////////////////////////////////

/// A row in `parent_nodes` table
#[derive(Debug, Clone, Eq, PartialEq, Identifiable, AsChangeset, Queryable)]
#[diesel(primary_key(rowid))]
pub struct ParentNode {
    /// SQLite rowid (so-called "integer primary key")
    pub rowid: i64,

    /// UUID of this parent node
    pub node_id: Id<Node>,

    /// URL prefix to connect to this parent node
    pub url_prefix: String,

    pub created_at: NaiveDateTime,
}

/// An `Insertable` parent node data
#[derive(Insertable, new)]
#[diesel(table_name = parent_nodes)]
pub struct NewParentNode<'a> {
    node_id: &'a Id<Node>,
    url_prefix: &'a str,
}

/////////////////////////////////////////////////////////////////////////////
// child_nodes
/////////////////////////////////////////////////////////////////////////////

/// A row in `child_nodes` table
#[derive(Debug, Clone, Eq, PartialEq, Identifiable, AsChangeset, Queryable)]
#[diesel(primary_key(rowid))]
pub struct ChildNode {
    /// SQLite rowid (so-called "integer primary key")
    pub rowid: i64,

    /// UUID of this child node
    pub node_id: Id<Node>,

    /// Password for authentication
    pub password_hash: String,

    /// Permission to edit links in this database
    pub can_edit_links: bool,

    pub created_at: NaiveDateTime,
}

/// An `Insertable` child node data
#[derive(Insertable, new)]
#[diesel(table_name = child_nodes)]
pub struct NewChildNode<'a> {
    node_id: &'a Id<Node>,
    password_hash: &'a str,

    // https://github.com/nrc/derive-new
    #[new(value = "false")]
    can_edit_links: bool,
}

/////////////////////////////////////////////////////////////////////////////
// imported_nodes
/////////////////////////////////////////////////////////////////////////////

/// A row in `imported_nodes` table
#[derive(Debug, Clone, Eq, PartialEq, Identifiable, AsChangeset, Queryable)]
#[diesel(primary_key(rowid))]
pub struct ImportedNode {
    /// SQLite rowid (so-called "integer primary key")
    pub rowid: i64,

    /// UUID of this node imported in this database
    pub node_id: Id<Node>,

    pub created_at: NaiveDateTime,
}

/// An `Insertable` imported node data
#[derive(Insertable, new)]
#[diesel(table_name = imported_nodes)]
pub struct NewImportedNode<'a> {
    node_id: &'a Id<Node>,
}
