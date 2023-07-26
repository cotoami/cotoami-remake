use anyhow::{anyhow, Result};
use chrono::{DateTime, Duration, Local, NaiveDateTime, TimeZone};
use diesel::prelude::*;
use validator::Validate;

use super::Node;
use crate::{models::Id, schema::local_node};

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

    /// Node owner's session token
    pub owner_session_token: Option<String>,

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

    pub fn start_owner_session(&mut self, password: &str, duration: Duration) -> Result<&str> {
        self.verify_owner_password(password)?;
        self.owner_session_token = Some(crate::generate_session_token());
        self.owner_session_expires_at = Some(crate::current_datetime() + duration);
        Ok(self.owner_session_token.as_ref().unwrap())
    }

    pub fn verify_owner_session(&self, token: &str) -> Result<()> {
        if let Some(expires_at) = self.owner_session_expires_at {
            if expires_at < crate::current_datetime() {
                return Err(anyhow!("Owner session has been expired."));
            }
        }
        if let Some(session_token) = self.owner_session_token.as_ref() {
            if token != session_token {
                return Err(anyhow!("The passed session token is invalid."));
            }
        } else {
            return Err(anyhow!("Owner session doesn't exist."));
        }
        Ok(())
    }

    pub fn clear_owner_session(&mut self) {
        self.owner_session_token = None;
        self.owner_session_expires_at = None;
    }

    fn verify_owner_password(&self, password: &str) -> Result<()> {
        let password_hash = self
            .owner_password_hash
            .as_ref()
            .ok_or(anyhow!("No owner password assigned."))?;
        super::verify_password(password, password_hash)
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
        local_node.update_owner_password("foo")?;

        // then
        assert!(local_node.verify_owner_password("foo").is_ok());
        assert!(local_node.verify_owner_password("bar").is_err());

        Ok(())
    }
}
