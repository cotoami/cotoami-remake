use anyhow::Result;
use chrono::NaiveDateTime;
use diesel::prelude::*;

use super::{Node, Principal};
use crate::{models::Id, schema::child_nodes};

/// A row in `child_nodes` table
#[derive(Debug, Clone, PartialEq, Eq, Identifiable, AsChangeset, Queryable, Selectable)]
#[diesel(primary_key(node_id), treat_none_as_null = true)]
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

impl Principal for ChildNode {
    fn password_hash(&self) -> Option<&str> { Some(&self.password_hash) }

    fn set_password_hash(&mut self, hash: Option<String>) {
        self.password_hash = hash.unwrap_or_default();
    }

    fn session_token(&self) -> Option<&str> { self.session_token.as_deref() }

    fn set_session_token(&mut self, token: Option<String>) { self.session_token = token; }

    fn session_expires_at(&self) -> Option<&NaiveDateTime> { self.session_expires_at.as_ref() }

    fn set_session_expires_at(&mut self, expires_at: Option<NaiveDateTime>) {
        self.session_expires_at = expires_at;
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
