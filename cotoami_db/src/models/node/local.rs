use std::borrow::{Borrow, Cow};

use anyhow::Result;
use chrono::NaiveDateTime;
use derive_new::new;
use diesel::prelude::*;
use validator::Validate;

use super::{Node, Principal};
use crate::{models::Id, schema::local_node};

/////////////////////////////////////////////////////////////////////////////
// LocalNode
/////////////////////////////////////////////////////////////////////////////

/// A row in `local_node` table
#[derive(derive_more::Debug, Clone, PartialEq, Eq, Identifiable, Queryable, Selectable)]
#[diesel(table_name = local_node, primary_key(node_id))]
pub struct LocalNode {
    /// UUID of a local node
    pub node_id: Id<Node>,

    /// SQLite rowid (so-called "integer primary key")
    pub rowid: i64,

    /// Password for owner authentication of this local node
    #[debug(skip)]
    pub owner_password_hash: Option<String>,

    /// Node owner's session token
    #[debug(skip)]
    pub owner_session_token: Option<String>,

    /// Expiration date of node owner's session
    pub owner_session_expires_at: Option<NaiveDateTime>,

    /// The maximum length of the longer side of images after they are resized (in pixels).
    pub image_max_size: Option<i32>,
}

impl LocalNode {
    pub fn as_principal(&self) -> NodeOwner {
        NodeOwner {
            node_id: &self.node_id,
            owner_password_hash: self.owner_password_hash.as_ref().map(Cow::from),
            owner_session_token: self.owner_session_token.as_ref().map(Cow::from),
            owner_session_expires_at: self.owner_session_expires_at,
        }
    }

    pub(crate) fn to_update(&self) -> UpdateLocalNode { UpdateLocalNode::new(&self.node_id) }
}

/////////////////////////////////////////////////////////////////////////////
// NewLocalNode
/////////////////////////////////////////////////////////////////////////////

/// An `Insertable` local node data
#[derive(Insertable, Validate)]
#[diesel(table_name = local_node)]
pub(crate) struct NewLocalNode<'a> {
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

/////////////////////////////////////////////////////////////////////////////
// NodeOwner (LocalNode as Principal)
/////////////////////////////////////////////////////////////////////////////

#[derive(derive_more::Debug, Identifiable, AsChangeset)]
#[diesel(table_name = local_node, primary_key(node_id), treat_none_as_null = true)]
pub struct NodeOwner<'a> {
    node_id: &'a Id<Node>,

    #[debug(skip)]
    owner_password_hash: Option<Cow<'a, str>>,

    #[debug(skip)]
    owner_session_token: Option<Cow<'a, str>>,

    owner_session_expires_at: Option<NaiveDateTime>,
}

impl<'a> Principal for NodeOwner<'a> {
    fn password_hash(&self) -> Option<&str> {
        self.owner_password_hash.as_ref().map(Borrow::borrow)
    }

    fn set_password_hash(&mut self, hash: Option<String>) {
        self.owner_password_hash = hash.map(Cow::from);
    }

    fn session_token(&self) -> Option<&str> { self.owner_session_token.as_deref() }

    fn set_session_token(&mut self, token: Option<String>) {
        self.owner_session_token = token.map(Cow::from);
    }

    fn session_expires_at(&self) -> Option<&NaiveDateTime> {
        self.owner_session_expires_at.as_ref()
    }

    fn set_session_expires_at(&mut self, expires_at: Option<NaiveDateTime>) {
        self.owner_session_expires_at = expires_at;
    }
}

/////////////////////////////////////////////////////////////////////////////
// UpdateLocalNode
/////////////////////////////////////////////////////////////////////////////

/// A changeset of [LocalNode] for update.
/// Only fields that have [Some] value will be updated.
#[derive(Debug, Identifiable, AsChangeset, Validate, new)]
#[diesel(table_name = local_node, primary_key(node_id))]
pub(crate) struct UpdateLocalNode<'a> {
    node_id: &'a Id<Node>,

    #[new(default)]
    #[validate(range(min = 1))]
    pub image_max_size: Option<Option<i32>>,
}

/////////////////////////////////////////////////////////////////////////////
// tests
/////////////////////////////////////////////////////////////////////////////

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use anyhow::Result;

    use super::*;

    #[test]
    fn owner_password() -> Result<()> {
        // setup
        let local_node = LocalNode {
            node_id: Id::from_str("00000000-0000-0000-0000-000000000001")?,
            rowid: 1,
            owner_password_hash: None,
            owner_session_token: None,
            owner_session_expires_at: None,
            image_max_size: None,
        };
        let mut owner = local_node.as_principal();

        // when
        owner.update_password("foo")?;

        // then
        assert!(owner.verify_password("foo").is_ok());
        assert!(owner.verify_password("bar").is_err());

        Ok(())
    }
}
