use anyhow::{anyhow, Result};
use chrono::{DateTime, Duration, Local, NaiveDateTime, TimeZone};
use diesel::prelude::*;

use super::Node;
use crate::{models::Id, schema::child_nodes};

/// A row in `child_nodes` table
#[derive(Debug, Clone, PartialEq, Eq, Identifiable, AsChangeset, Queryable)]
#[diesel(primary_key(node_id))]
pub struct ChildNode {
    /// UUID of this child node
    pub node_id: Id<Node>,

    /// Password for authentication
    pub password_hash: String,

    /// Login session token
    pub session_token: Option<String>,

    /// Expiration date of login session
    pub session_expires_at: Option<NaiveDateTime>,

    /// TRUE if this node has the same permission as the owner
    pub as_owner: bool,

    /// Permission to edit links in this database
    pub can_edit_links: bool,

    pub created_at: NaiveDateTime,
}

impl ChildNode {
    pub fn session_expires_at(&self) -> Option<DateTime<Local>> {
        self.session_expires_at
            .map(|expires_at| Local.from_utc_datetime(&expires_at))
    }

    pub fn update_password(&mut self, password: &str) -> Result<()> {
        self.password_hash = super::hash_password(password.as_bytes())?;
        Ok(())
    }

    pub fn start_owner_session(&mut self, password: &str, duration: Duration) -> Result<&str> {
        self.verify_password(password)?;
        self.session_token = Some(crate::generate_session_token());
        self.session_expires_at = Some(crate::current_datetime() + duration);
        Ok(self.session_token.as_deref().unwrap())
    }

    pub fn verify_session(&self, token: &str) -> Result<()> {
        if let Some(expires_at) = self.session_expires_at {
            if expires_at < crate::current_datetime() {
                return Err(anyhow!("Session has been expired."));
            }
        }
        if let Some(session_token) = self.session_token.as_deref() {
            if token != session_token {
                return Err(anyhow!("The passed session token is invalid."));
            }
        } else {
            return Err(anyhow!("Session doesn't exist."));
        }
        Ok(())
    }

    pub fn clear_session(&mut self) {
        self.session_token = None;
        self.session_expires_at = None;
    }

    fn verify_password(&self, password: &str) -> Result<()> {
        super::verify_password(password, &self.password_hash)
    }
}

/// An `Insertable` child node data
#[derive(Insertable)]
#[diesel(table_name = child_nodes)]
pub struct NewChildNode<'a> {
    node_id: &'a Id<Node>,
    password_hash: String,
    as_owner: bool,
    can_edit_links: bool,
    created_at: NaiveDateTime,
}

impl<'a> NewChildNode<'a> {
    pub fn new(
        node_id: &'a Id<Node>,
        password: &'a str,
        as_owner: bool,
        can_edit_links: bool,
    ) -> Result<Self> {
        let password_hash = super::hash_password(password.as_bytes())?;
        Ok(Self {
            node_id,
            password_hash,
            as_owner,
            can_edit_links,
            created_at: crate::current_datetime(),
        })
    }
}
