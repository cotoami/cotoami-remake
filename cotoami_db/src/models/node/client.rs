use std::borrow::{Borrow, Cow};

use anyhow::Result;
use chrono::NaiveDateTime;
use derive_new::new;
use diesel::prelude::*;
use validator::Validate;

use super::{Node, Principal};
use crate::{models::Id, schema::client_nodes};

/////////////////////////////////////////////////////////////////////////////
// ClientNode
/////////////////////////////////////////////////////////////////////////////

/// A row in `client_nodes` table
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
#[diesel(primary_key(node_id))]
pub struct ClientNode {
    /// UUID of this client node
    pub node_id: Id<Node>,

    /// Date when this account was created.
    pub created_at: NaiveDateTime,

    /// Password for authentication
    #[debug(skip)]
    #[serde(skip_serializing, skip_deserializing)]
    pub password_hash: String,

    /// Login session token
    #[debug(skip)]
    #[serde(skip_serializing, skip_deserializing)]
    pub session_token: Option<String>,

    /// Expiration date of login session
    pub session_expires_at: Option<NaiveDateTime>,

    /// Local node won't allow this node to connect to it if the value is TRUE.
    pub disabled: bool,

    /// Timestamp when the last session was created.
    pub last_session_created_at: Option<NaiveDateTime>,
}

impl ClientNode {
    pub fn as_principal(&self) -> ClientNodeAsPrincipal {
        ClientNodeAsPrincipal {
            node_id: &self.node_id,
            password_hash: Cow::from(&self.password_hash),
            session_token: self.session_token.as_ref().map(Cow::from),
            session_expires_at: self.session_expires_at,
            last_session_created_at: self.last_session_created_at,
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// NewClientNode
/////////////////////////////////////////////////////////////////////////////

/// An `Insertable` client node data
#[derive(Insertable)]
#[diesel(table_name = client_nodes)]
pub(crate) struct NewClientNode<'a> {
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

/////////////////////////////////////////////////////////////////////////////
// ClientNodeAsPrincipal
/////////////////////////////////////////////////////////////////////////////

#[derive(derive_more::Debug, Identifiable, AsChangeset)]
#[diesel(table_name = client_nodes, primary_key(node_id), treat_none_as_null = true)]
pub struct ClientNodeAsPrincipal<'a> {
    node_id: &'a Id<Node>,

    #[debug(skip)]
    password_hash: Cow<'a, str>,

    #[debug(skip)]
    session_token: Option<Cow<'a, str>>,

    session_expires_at: Option<NaiveDateTime>,

    last_session_created_at: Option<NaiveDateTime>,
}

impl<'a> Principal for ClientNodeAsPrincipal<'a> {
    fn password_hash(&self) -> Option<&str> { Some(self.password_hash.borrow()) }

    fn set_password_hash(&mut self, hash: Option<String>) {
        self.password_hash = Cow::from(hash.unwrap_or_default());
    }

    fn session_token(&self) -> Option<&str> { self.session_token.as_deref() }

    fn set_session_token(&mut self, token: Option<String>) {
        self.session_token = token.map(Cow::from);
        self.last_session_created_at = Some(crate::current_datetime());
    }

    fn session_expires_at(&self) -> Option<&NaiveDateTime> { self.session_expires_at.as_ref() }

    fn set_session_expires_at(&mut self, expires_at: Option<NaiveDateTime>) {
        self.session_expires_at = expires_at;
    }
}

/////////////////////////////////////////////////////////////////////////////
// UpdateClientNode
/////////////////////////////////////////////////////////////////////////////

/// A changeset of [ClientNode] for update.
/// Only fields that have [Some] value will be updated.
#[derive(Debug, Identifiable, AsChangeset, Validate, new)]
#[diesel(table_name = client_nodes, primary_key(node_id))]
pub(crate) struct UpdateClientNode<'a> {
    node_id: &'a Id<Node>,

    #[new(default)]
    pub disabled: Option<bool>,
}
