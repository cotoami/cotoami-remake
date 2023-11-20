use anyhow::Result;
use chrono::NaiveDateTime;
use diesel::prelude::*;

use super::{Node, Principal};
use crate::{models::Id, schema::client_nodes};

/////////////////////////////////////////////////////////////////////////////
// ClientNode
/////////////////////////////////////////////////////////////////////////////

/// A row in `client_nodes` table
#[derive(
    derive_more::Debug, Clone, PartialEq, Eq, Identifiable, AsChangeset, Queryable, Selectable,
)]
#[diesel(primary_key(node_id), treat_none_as_null = true)]
pub struct ClientNode {
    /// UUID of this client node
    pub node_id: Id<Node>,

    /// Date when this account was created.
    pub created_at: NaiveDateTime,

    /// Password for authentication
    #[debug(skip)]
    pub password_hash: String,

    /// Login session token
    #[debug(skip)]
    pub session_token: Option<String>,

    /// Expiration date of login session
    pub session_expires_at: Option<NaiveDateTime>,

    /// Local node won't allow this node to connect to it if the value is TRUE.
    pub disabled: bool,
}

impl Principal for ClientNode {
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

/////////////////////////////////////////////////////////////////////////////
// NewClientNode
/////////////////////////////////////////////////////////////////////////////

/// An `Insertable` client node data
#[derive(Insertable)]
#[diesel(table_name = client_nodes)]
pub struct NewClientNode<'a> {
    node_id: &'a Id<Node>,
    created_at: NaiveDateTime,
    password_hash: String,
}

impl<'a> NewClientNode<'a> {
    pub fn new(node_id: &'a Id<Node>, password: &'a str) -> Result<Self> {
        let password_hash = super::hash_password(password.as_bytes())?;
        Ok(Self {
            node_id,
            created_at: crate::current_datetime(),
            password_hash,
        })
    }
}
