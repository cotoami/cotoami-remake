//! A node is a single Cotoami database that has connections to/from other databases(nodes).

use anyhow::{anyhow, bail, Result};
use argon2::{
    password_hash::{rand_core::OsRng, PasswordHash, PasswordHasher, PasswordVerifier, SaltString},
    Argon2,
};
use chrono::{DateTime, Duration, Local, NaiveDateTime, TimeZone};
use diesel::prelude::*;
use identicon_rs::Identicon;
use validator::Validate;

use crate::{
    models::{cotonoma::Cotonoma, Bytes, Id},
    schema::nodes,
};

pub mod child;
pub mod client;
pub mod local;
pub mod parent;
pub mod roles;
pub mod server;

/////////////////////////////////////////////////////////////////////////////
// Node
/////////////////////////////////////////////////////////////////////////////

/// A row in `nodes` table
#[derive(
    derive_more::Debug,
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
    #[serde(
        serialize_with = "crate::as_base64",
        deserialize_with = "Bytes::from_base64"
    )]
    #[debug(skip)]
    pub icon: Bytes,

    /// Display name which syncs with the name of the root cotonoma.
    ///
    /// The value will be an empty string if `root_cotonoma_id` is None.
    pub name: String,

    /// UUID of the root cotonoma of this node
    pub root_cotonoma_id: Option<Id<Cotonoma>>,

    /// Version of node info for synchronizing among databases
    pub version: i32,

    /// Creation date of this node
    pub created_at: NaiveDateTime,
}

impl Node {
    pub const ICON_MAX_LENGTH: usize = 5_000_000; // 5MB
    pub const NAME_MAX_LENGTH: usize = Cotonoma::NAME_MAX_LENGTH;

    pub fn created_at(&self) -> DateTime<Local> { Local.from_utc_datetime(&self.created_at) }

    pub fn to_update(&self) -> UpdateNode {
        UpdateNode {
            uuid: &self.uuid,
            icon: self.icon.as_ref(),
            name: &self.name,
            root_cotonoma_id: self.root_cotonoma_id.as_ref(),
            version: self.version + 1, // increment the version
        }
    }

    /// Converting a foreign node into an importable data.
    ///
    /// - It assumes the node data came from another node.
    /// - `created_at` is the original date of the node creation, so it should be kept.
    pub fn to_import(&self) -> ImportNode {
        ImportNode {
            uuid: &self.uuid,
            icon: &self.icon,
            name: &self.name,
            root_cotonoma_id: self.root_cotonoma_id.as_ref(),
            version: self.version,
            created_at: &self.created_at,
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// NewNode
/////////////////////////////////////////////////////////////////////////////

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
}

impl<'a> NewNode<'a> {
    pub fn new(name: &'a str) -> Result<Self> {
        let uuid = Id::generate();
        let icon_binary = generate_identicon(&uuid.to_string())?;
        let new_node = Self {
            uuid,
            icon: icon_binary,
            name,
            version: 1,
            created_at: crate::current_datetime(),
        };
        new_node.validate()?;
        Ok(new_node)
    }

    pub fn new_placeholder(uuid: Id<Node>) -> Self {
        Self {
            uuid,
            icon: Vec::default(),
            name: "",
            version: 0,
            created_at: crate::current_datetime(),
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// ImportNode
/////////////////////////////////////////////////////////////////////////////

/// An `Insertable/AsChangeset` node data for importing/upgrading a remote node
#[derive(Insertable, AsChangeset, Identifiable)]
#[diesel(table_name = nodes, primary_key(uuid))]
pub struct ImportNode<'a> {
    uuid: &'a Id<Node>,
    icon: &'a Bytes,
    name: &'a str,
    root_cotonoma_id: Option<&'a Id<Cotonoma>>,
    version: i32,
    created_at: &'a NaiveDateTime,
}

/////////////////////////////////////////////////////////////////////////////
// UpdateNode
/////////////////////////////////////////////////////////////////////////////

/// A changeset of a node for update
#[derive(Debug, Identifiable, AsChangeset, Validate)]
#[diesel(table_name = nodes, primary_key(uuid))]
pub struct UpdateNode<'a> {
    uuid: &'a Id<Node>,
    #[validate(length(max = "Node::ICON_MAX_LENGTH"))]
    pub icon: &'a [u8],
    #[validate(length(max = "Node::NAME_MAX_LENGTH"))]
    pub name: &'a str,
    pub root_cotonoma_id: Option<&'a Id<Cotonoma>>,
    version: i32,
}

/////////////////////////////////////////////////////////////////////////////
// BelongsToNode
/////////////////////////////////////////////////////////////////////////////

pub trait BelongsToNode {
    fn node_id(&self) -> &Id<Node>;
}

/////////////////////////////////////////////////////////////////////////////
// Principal
/////////////////////////////////////////////////////////////////////////////

/// Principal represents an entity that can authenticate itself by a password
/// or a session token.
pub trait Principal {
    fn password_hash(&self) -> Option<&str>;

    fn set_password_hash(&mut self, hash: Option<String>);

    fn session_token(&self) -> Option<&str>;

    fn set_session_token(&mut self, token: Option<String>);

    fn session_expires_at(&self) -> Option<&NaiveDateTime>;

    fn set_session_expires_at(&mut self, expires_at: Option<NaiveDateTime>);

    fn session_expires_at_as_local_time(&self) -> Option<DateTime<Local>> {
        self.session_expires_at()
            .map(|expires_at| Local.from_utc_datetime(expires_at))
    }

    fn update_password(&mut self, password: &str) -> Result<()> {
        let hash = hash_password(password.as_bytes())?;
        self.set_password_hash(Some(hash));
        Ok(())
    }

    fn authenticate(&self, password: Option<&str>) -> Result<()> {
        if let Some(password) = password {
            self.verify_password(password)?;
        } else {
            if self.password_hash().is_some() {
                bail!("A password must be specified to authenticate.");
            }
        }
        Ok(())
    }

    fn start_session(&mut self, password: &str, duration: Duration) -> Result<&str> {
        self.verify_password(password)?;
        self.set_session_token(Some(crate::generate_session_token()));
        self.set_session_expires_at(Some(crate::current_datetime() + duration));
        Ok(self.session_token().unwrap())
    }

    fn verify_session(&self, token: &str) -> Result<()> {
        if let Some(expires_at) = self.session_expires_at() {
            if *expires_at < crate::current_datetime() {
                bail!("Session has been expired.");
            }
        }
        if let Some(session_token) = self.session_token() {
            if token != session_token {
                bail!("The passed session token is invalid.");
            }
        } else {
            bail!("Session doesn't exist.");
        }
        Ok(())
    }

    fn clear_session(&mut self) {
        self.set_session_token(None);
        self.set_session_expires_at(None);
    }

    fn verify_password(&self, password: &str) -> Result<()> {
        let password_hash = self
            .password_hash()
            .ok_or(anyhow!("No owner password assigned."))?;
        verify_password(password, password_hash)
    }
}

/////////////////////////////////////////////////////////////////////////////
// Internal functions
/////////////////////////////////////////////////////////////////////////////

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
