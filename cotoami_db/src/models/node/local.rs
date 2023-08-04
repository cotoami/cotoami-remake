use anyhow::Result;
use chrono::NaiveDateTime;
use diesel::prelude::*;
use validator::Validate;

use super::{Node, Principal};
use crate::{models::Id, schema::local_node};

/// A row in `local_node` table
#[derive(Debug, Clone, Eq, PartialEq, Identifiable, AsChangeset, Queryable, Selectable)]
#[diesel(table_name = local_node, primary_key(node_id), treat_none_as_null = true)]
pub struct LocalNode {
    /// UUID of a local node
    pub node_id: Id<Node>,

    /// SQLite rowid (so-called "integer primary key")
    pub rowid: i64,

    /// Password for owner authentication of this local node
    pub owner_password_hash: Option<String>,

    /// Node owner's session token
    pub owner_session_token: Option<String>,

    /// Expiration date of node owner's session
    pub owner_session_expires_at: Option<NaiveDateTime>,
}

impl Principal for LocalNode {
    fn password_hash(&self) -> Option<&str> { self.owner_password_hash.as_deref() }

    fn set_password_hash(&mut self, hash: Option<String>) { self.owner_password_hash = hash; }

    fn session_token(&self) -> Option<&str> { self.owner_session_token.as_deref() }

    fn set_session_token(&mut self, token: Option<String>) { self.owner_session_token = token; }

    fn session_expires_at(&self) -> Option<&NaiveDateTime> {
        self.owner_session_expires_at.as_ref()
    }

    fn set_session_expires_at(&mut self, expires_at: Option<NaiveDateTime>) {
        self.owner_session_expires_at = expires_at;
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

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use anyhow::Result;

    use super::*;

    #[test]
    fn owner_password() -> Result<()> {
        // setup
        let mut local_node = LocalNode {
            node_id: Id::from_str("00000000-0000-0000-0000-000000000001")?,
            rowid: 1,
            owner_password_hash: None,
            owner_session_token: None,
            owner_session_expires_at: None,
        };

        // when
        local_node.update_password("foo")?;

        // then
        assert!(local_node.verify_password("foo").is_ok());
        assert!(local_node.verify_password("bar").is_err());

        Ok(())
    }
}
