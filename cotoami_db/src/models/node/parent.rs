use anyhow::Result;
use chrono::NaiveDateTime;
use diesel::prelude::*;

use super::Node;
use crate::{models::Id, schema::parent_nodes};

/////////////////////////////////////////////////////////////////////////////
// ParentNode
/////////////////////////////////////////////////////////////////////////////

/// A row in `parent_nodes` table
#[derive(
    derive_more::Debug, Clone, Eq, PartialEq, Identifiable, AsChangeset, Queryable, Selectable,
)]
#[diesel(primary_key(node_id))]
pub struct ParentNode {
    /// UUID of this parent node.
    pub node_id: Id<Node>,

    /// Date when this parent was incorporated into the local node.
    pub created_at: NaiveDateTime,

    /// Number of changes received from this parent node.
    pub changes_received: i64,

    /// Date when received the last change from this parent node.
    pub last_change_received_at: Option<NaiveDateTime>,

    /// TRUE if the local node has been forked from this parent node.
    ///
    /// A forked child can never connect to or accept changes
    /// (directly or indirectly) from the parent again.
    pub forked: bool,
}

/////////////////////////////////////////////////////////////////////////////
// NewParentNode
/////////////////////////////////////////////////////////////////////////////

/// An `Insertable` parent node data
#[derive(Insertable)]
#[diesel(table_name = parent_nodes)]
pub struct NewParentNode<'a> {
    node_id: &'a Id<Node>,
    created_at: NaiveDateTime,
}

impl<'a> NewParentNode<'a> {
    pub fn new(node_id: &'a Id<Node>) -> Result<Self> {
        Ok(Self {
            node_id,
            created_at: crate::current_datetime(),
        })
    }
}
