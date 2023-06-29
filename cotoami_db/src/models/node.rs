//! A node is a single Cotoami database that has connections to/from other databases(nodes).

use super::coto::Cotonoma;
use super::Id;
use crate::schema::{child_nodes, incorporated_nodes, nodes};
use anyhow::Result;
use argon2::password_hash::rand_core::OsRng;
use argon2::password_hash::{PasswordHash, PasswordHasher, PasswordVerifier, SaltString};
use argon2::Argon2;
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use diesel::prelude::*;
use identicon_rs::Identicon;
use validator::Validate;

pub mod local;
pub mod parent;

/////////////////////////////////////////////////////////////////////////////
// nodes
/////////////////////////////////////////////////////////////////////////////

/// A row in `nodes` table
#[derive(
    Debug,
    Clone,
    PartialEq,
    Eq,
    Identifiable,
    Queryable,
    Selectable,
    serde::Serialize,
    serde::Deserialize,
)]
#[diesel(primary_key(uuid))]
pub struct Node {
    /// Universally unique node ID
    pub uuid: Id<Node>,

    /// SQLite rowid (so-called "integer primary key")
    #[serde(skip_serializing, skip_deserializing)]
    pub rowid: i64,

    /// Icon image
    pub icon: Vec<u8>,

    /// Display name
    pub name: String,

    /// UUID of the root cotonoma of this node
    pub root_cotonoma_id: Option<Id<Cotonoma>>,

    /// Version of node info for synchronizing among databases
    pub version: i32,

    /// Creation date of this node
    pub created_at: NaiveDateTime,

    /// Registration date in this database
    pub inserted_at: NaiveDateTime,
}

impl Node {
    pub const ICON_MAX_LENGTH: usize = 5_000_000; // 5MB
    pub const NAME_MAX_LENGTH: usize = Cotonoma::NAME_MAX_LENGTH;

    pub fn created_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.created_at)
    }

    pub fn inserted_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.inserted_at)
    }

    pub fn to_update(&self) -> UpdateNode {
        UpdateNode {
            uuid: &self.uuid,
            icon: &self.icon,
            name: &self.name,
            root_cotonoma_id: self.root_cotonoma_id.as_ref(),
            version: self.version + 1, // increment the version
        }
    }

    /// Converting a foreign node into an importable data.
    ///
    /// - It assumes the node data came from another node (parent node).
    /// - `owner_password_hash` will be `None` (it should not be sent in the first place).
    /// - `created_at` is the original date of the node creation, so it should be kept.
    pub fn to_import(&self) -> ImportNode {
        ImportNode {
            uuid: &self.uuid,
            icon: &self.icon,
            name: &self.name,
            root_cotonoma_id: self.root_cotonoma_id.as_ref(),
            version: self.version,
            created_at: &self.created_at,
            inserted_at: crate::current_datetime(),
        }
    }
}

/// An `Insertable/AsChangeset` node data for importing/upgrading a remote node
#[derive(Insertable, AsChangeset, Identifiable)]
#[diesel(table_name = nodes, primary_key(uuid))]
pub struct ImportNode<'a> {
    uuid: &'a Id<Node>,
    icon: &'a Vec<u8>,
    name: &'a str,
    root_cotonoma_id: Option<&'a Id<Cotonoma>>,
    version: i32,
    created_at: &'a NaiveDateTime,
    inserted_at: NaiveDateTime,
}

/// An `Insertable` new node
#[derive(Insertable, Validate)]
#[diesel(table_name = nodes)]
pub struct NewNode<'a> {
    uuid: Id<Node>,
    #[validate(length(max = "Node::ICON_MAX_LENGTH"))]
    icon: Vec<u8>,
    #[validate(length(max = "Node::NAME_MAX_LENGTH"))]
    name: &'a str,
    version: i32,
    created_at: NaiveDateTime,
    inserted_at: NaiveDateTime,
}

impl<'a> NewNode<'a> {
    /// Create a local node
    pub fn new_local(name: &'a str) -> Result<Self> {
        let uuid = Id::generate();
        let icon_binary = generate_identicon(&uuid.to_string())?;
        let now = crate::current_datetime();
        let new_node = Self {
            uuid,
            icon: icon_binary,
            name,
            version: 1,
            created_at: now,
            inserted_at: now,
        };
        new_node.validate()?;
        Ok(new_node)
    }
}

/// Generates a new identicon from an input value.
///
/// The defaults are:
/// - border: 50
/// - size: 5
/// - scale: 500
/// - background_color: (240, 240, 240)
/// - mirrored: true
/// <https://github.com/conways-glider/identicon-rs/blob/main/src/lib.rs#L54>
fn generate_identicon(id: &str) -> Result<Vec<u8>> {
    let icon_binary = Identicon::new(id).export_png_data()?;
    Ok(icon_binary)
}

/// A changeset of a node for update
#[derive(Debug, Identifiable, AsChangeset, Validate)]
#[diesel(table_name = nodes, primary_key(uuid))]
pub struct UpdateNode<'a> {
    uuid: &'a Id<Node>,
    #[validate(length(max = "Node::ICON_MAX_LENGTH"))]
    pub icon: &'a Vec<u8>,
    #[validate(length(max = "Node::NAME_MAX_LENGTH"))]
    pub name: &'a str,
    pub root_cotonoma_id: Option<&'a Id<Cotonoma>>,
    version: i32,
}

fn hash_password(password: &[u8]) -> Result<String> {
    let salt = SaltString::generate(&mut OsRng);
    let argon2 = Argon2::default();
    let password_hash = argon2.hash_password(password, &salt)?.to_string();
    Ok(password_hash)
}

fn verify_password(password: &str, password_hash: &str) -> Result<()> {
    let parsed_hash = PasswordHash::new(password_hash)?;
    Argon2::default().verify_password(password.as_bytes(), &parsed_hash)?;
    Ok(())
}

/////////////////////////////////////////////////////////////////////////////
// child_nodes
/////////////////////////////////////////////////////////////////////////////

/// A row in `child_nodes` table
#[derive(Debug, Clone, PartialEq, Eq, Identifiable, AsChangeset, Queryable)]
#[diesel(primary_key(node_id))]
pub struct ChildNode {
    /// UUID of this child node
    pub node_id: Id<Node>,

    /// Password for authentication
    pub password_hash: String,

    /// Permission to edit links in this database
    pub can_edit_links: bool,

    pub created_at: NaiveDateTime,
}

/// An `Insertable` child node data
#[derive(Insertable)]
#[diesel(table_name = child_nodes)]
pub struct NewChildNode<'a> {
    node_id: &'a Id<Node>,
    password_hash: &'a str,
    can_edit_links: bool,
    created_at: NaiveDateTime,
}

impl<'a> NewChildNode<'a> {
    pub fn new(node_id: &'a Id<Node>, password_hash: &'a str) -> Self {
        Self {
            node_id,
            password_hash,
            can_edit_links: false,
            created_at: crate::current_datetime(),
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// incorporated_nodes
/////////////////////////////////////////////////////////////////////////////

/// A row in `incorporated_nodes` table
#[derive(Debug, Clone, PartialEq, Eq, Identifiable, Queryable)]
#[diesel(primary_key(node_id))]
pub struct IncorporatedNode {
    /// UUID of this node incorporated in this database
    pub node_id: Id<Node>,

    pub created_at: NaiveDateTime,
}

/// An `Insertable` incorporated node data
#[derive(Insertable)]
#[diesel(table_name = incorporated_nodes)]
pub struct NewIncorporatedNode<'a> {
    node_id: &'a Id<Node>,
    created_at: NaiveDateTime,
}

impl<'a> NewIncorporatedNode<'a> {
    pub fn new(node_id: &'a Id<Node>) -> Self {
        Self {
            node_id,
            created_at: crate::current_datetime(),
        }
    }
}
