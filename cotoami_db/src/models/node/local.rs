use super::Node;
use crate::models::Id;
use crate::schema::local_node;
use anyhow::{anyhow, Result};
use chrono::{DateTime, Duration, Local, NaiveDateTime, TimeZone};
use diesel::prelude::*;
use rand::distributions::Alphanumeric;
use rand::{thread_rng, Rng};
use validator::Validate;

/// A row in `local_node` table
#[derive(Debug, Clone, Eq, PartialEq, Identifiable, AsChangeset, Queryable, Selectable)]
#[diesel(table_name = local_node, primary_key(node_id))]
pub struct LocalNode {
    /// UUID of a local node
    pub node_id: Id<Node>,

    /// SQLite rowid (so-called "integer primary key")
    pub rowid: i64,

    /// Password for owner authentication of this local node
    pub owner_password_hash: Option<String>,

    /// Node owner's session key
    pub owner_session_key: Option<String>,

    /// Expiration date of node owner's session
    pub owner_session_expires_at: Option<NaiveDateTime>,
}

impl LocalNode {
    pub fn owner_session_expires_at(&self) -> Option<DateTime<Local>> {
        self.owner_session_expires_at
            .map(|expires_at| Local.from_utc_datetime(&expires_at))
    }

    pub fn update_owner_password(&mut self, password: &str) -> Result<()> {
        let password_hash = super::hash_password(password.as_bytes())?;
        self.owner_password_hash = Some(password_hash);
        Ok(())
    }

    pub fn verify_owner_password(&self, password: &str) -> Result<()> {
        let password_hash = self
            .owner_password_hash
            .as_ref()
            .ok_or(anyhow!("No owner password assigned."))?;
        super::verify_password(password, password_hash)
    }

    pub fn start_owner_session(&mut self, password: &str, duration: Duration) -> Result<&str> {
        self.verify_owner_password(password)?;
        self.owner_session_key = Some(generate_session_key());
        self.owner_session_expires_at = Some(crate::current_datetime() + duration);
        Ok(self.owner_session_key.as_ref().unwrap())
    }

    pub fn verify_owner_session(&self, key: &str) -> Result<()> {
        if let Some(expires_at) = self.owner_session_expires_at {
            if expires_at < crate::current_datetime() {
                return Err(anyhow!("Owner session has been expired."));
            }
        }
        if let Some(session_key) = self.owner_session_key.as_ref() {
            if key != session_key {
                return Err(anyhow!("The passed session key is invalid."));
            }
        } else {
            return Err(anyhow!("Owner session doesn't exist."));
        }
        Ok(())
    }

    pub fn clear_owner_session(&mut self) {
        self.owner_session_key = None;
        self.owner_session_expires_at = None;
    }
}

/// An `Insertable` local node data
#[derive(Insertable, Validate)]
#[diesel(table_name = local_node)]
pub struct NewLocalNode<'a> {
    rowid: i64,
    node_id: &'a Id<Node>,
    owner_password_hash: Option<String>,
}

impl<'a> NewLocalNode<'a> {
    /// There can be only one row with `rowid=1`
    pub const SINGLETON_ROWID: i64 = 1;

    pub fn new(node_id: &'a Id<Node>, password: Option<&'a str>) -> Result<Self> {
        let owner_password_hash = if let Some(p) = password {
            Some(super::hash_password(p.as_bytes())?)
        } else {
            None
        };
        Ok(Self {
            rowid: Self::SINGLETON_ROWID,
            node_id,
            owner_password_hash,
        })
    }
}

fn generate_session_key() -> String {
    // https://rust-lang-nursery.github.io/rust-cookbook/algorithms/randomness.html#create-random-passwords-from-a-set-of-alphanumeric-characters
    thread_rng()
        .sample_iter(&Alphanumeric)
        .take(32)
        .map(char::from)
        .collect()
}
