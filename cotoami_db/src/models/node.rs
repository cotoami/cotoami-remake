use super::Id;
use crate::schema::nodes;
use anyhow::{anyhow, Result};
use argon2::password_hash::rand_core::OsRng;
use argon2::password_hash::{PasswordHash, PasswordHasher, PasswordVerifier, SaltString};
use argon2::Argon2;
use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
use diesel::prelude::*;
use identicon_rs::Identicon;

/// A node is a single cotoami database that has connections to/from other databases(nodes).
///
/// Identifiable:
/// * This struct represents a single row in a database table `nodes`.
/// * It will assume that the table name is the plural `snake_case` form of this struct name.
/// * It allows you to pass this struct to `update`.
///
/// Queryable:
/// * This struct represents the result of a SQL query.
/// * It assumes that the order of fields on this struct matches the columns of `nodes` in `schema.rs`.
#[derive(
    Debug, Clone, Eq, PartialEq, Identifiable, Queryable, serde::Serialize, serde::Deserialize,
)]
#[diesel(primary_key(rowid))]
pub struct Node {
    /// SQLite rowid (so-called "integer primary key")
    #[serde(skip_serializing, skip_deserializing)]
    pub rowid: i64,

    /// Globally unique node ID
    pub uuid: Id<Node>,

    /// Display name
    pub name: String,

    /// Icon image
    pub icon: Vec<u8>,

    /// For nodes being connected from this node
    pub url_prefix: Option<String>,

    /// For authenticating nodes connecting to this node
    pub password_hash: Option<String>,

    /// Permission to edit links in the database of this node.
    pub can_edit_links: bool,

    /// Version of node info for synchronizing among databases
    pub version: i32,

    /// Creation date of this node
    pub created_at: NaiveDateTime,

    /// Registration date in this database
    pub inserted_at: NaiveDateTime,
}

impl Node {
    pub const ROWID_FOR_SELF: i64 = 1;

    pub fn created_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.created_at)
    }

    pub fn inserted_at(&self) -> DateTime<Local> {
        Local.from_utc_datetime(&self.inserted_at)
    }

    pub fn update_password<'a>(&mut self, password: &'a str) -> Result<()> {
        let password_hash = hash_password(password.as_bytes())?;
        self.password_hash = Some(password_hash);
        Ok(())
    }

    pub fn verify_password<'a>(&self, password: &'a str) -> Result<()> {
        let password_hash = self
            .password_hash
            .as_ref()
            .ok_or(anyhow!("This node has no password assigned."))?;
        let parsed_hash = PasswordHash::new(&password_hash)?;
        Argon2::default().verify_password(password.as_bytes(), &parsed_hash)?;
        Ok(())
    }
}

#[derive(Insertable)]
#[diesel(table_name = nodes)]
pub struct NewNode<'a> {
    rowid: Option<i64>,
    uuid: Id<Node>,
    name: &'a str,
    icon: Vec<u8>,
    url_prefix: Option<&'a str>,
    password_hash: Option<String>,
    can_edit_links: bool,
    version: i32,
    created_at: Option<NaiveDateTime>,
    inserted_at: Option<NaiveDateTime>,
}

impl<'a> NewNode<'a> {
    /// Create a desktop node that represents **this** database.
    pub fn new_desktop(name: &'a str) -> Result<Self> {
        let icon_binary = generate_identicon(name)?;
        Ok(Self {
            rowid: Some(Node::ROWID_FOR_SELF),
            uuid: Id::generate(),
            name,
            icon: icon_binary,
            url_prefix: None,
            password_hash: None,
            can_edit_links: true,
            version: 1,
            created_at: None,
            inserted_at: None,
        })
    }

    /// Create a server node that represents **this** database.
    pub fn new_server(name: &'a str, password: &'a str) -> Result<Self> {
        let icon_binary = generate_identicon(name)?;
        let password_hash = hash_password(password.as_bytes())?;
        Ok(Self {
            rowid: Some(Node::ROWID_FOR_SELF),
            uuid: Id::generate(),
            name,
            icon: icon_binary,
            url_prefix: None,
            password_hash: Some(password_hash),
            can_edit_links: true,
            version: 1,
            created_at: None,
            inserted_at: None,
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
