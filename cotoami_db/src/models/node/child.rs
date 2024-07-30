use anyhow::Result;
use chrono::NaiveDateTime;
use derive_new::new;
use diesel::prelude::*;
use validator::Validate;

use super::Node;
use crate::{models::Id, schema::child_nodes};

/////////////////////////////////////////////////////////////////////////////
// ChildNode
/////////////////////////////////////////////////////////////////////////////

/// A row in `child_nodes` table
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
#[diesel(primary_key(node_id), treat_none_as_null = true)]
pub struct ChildNode {
    /// UUID of a child node.
    pub node_id: Id<Node>,

    /// Date when this child incorporated the local node.
    pub created_at: NaiveDateTime,

    /// TRUE if this node has the same permission as the owner.
    pub as_owner: bool,

    /// Permission to edit links in this database.
    pub can_edit_links: bool,
}

/////////////////////////////////////////////////////////////////////////////
// NewChildNode
/////////////////////////////////////////////////////////////////////////////

/// An `Insertable` child node data
#[derive(Insertable)]
#[diesel(table_name = child_nodes)]
pub(crate) struct NewChildNode<'a> {
    node_id: &'a Id<Node>,
    created_at: NaiveDateTime,
    as_owner: bool,
    can_edit_links: bool,
}

impl<'a> NewChildNode<'a> {
    pub fn new(node_id: &'a Id<Node>, as_owner: bool, can_edit_links: bool) -> Result<Self> {
        Ok(Self {
            node_id,
            created_at: crate::current_datetime(),
            as_owner,
            can_edit_links,
        })
    }
}

/////////////////////////////////////////////////////////////////////////////
// UpdateChildNode
/////////////////////////////////////////////////////////////////////////////

/// A changeset of [ChildNode] for update.
/// Only fields that have [Some] value will be updated.
#[derive(Debug, Identifiable, AsChangeset, Validate, new)]
#[diesel(table_name = child_nodes, primary_key(node_id))]
pub(crate) struct UpdateChildNode<'a> {
    node_id: &'a Id<Node>,

    #[new(default)]
    pub as_owner: Option<bool>,

    #[new(default)]
    pub can_edit_links: Option<bool>,
}
