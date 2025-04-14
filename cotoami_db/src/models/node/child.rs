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

    /// Permission to edit itos in this database.
    pub can_edit_itos: bool,

    /// Permission to post cotonomas in this database.
    pub can_post_cotonomas: bool,
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
    can_edit_itos: bool,
    can_post_cotonomas: bool,
}

impl<'a> NewChildNode<'a> {
    pub fn new(node_id: &'a Id<Node>, input: &ChildNodeInput) -> Result<Self> {
        Ok(Self {
            node_id,
            created_at: crate::current_datetime(),
            as_owner: input.as_owner,
            can_edit_itos: input.can_edit_itos,
            can_post_cotonomas: input.can_post_cotonomas,
        })
    }
}

/////////////////////////////////////////////////////////////////////////////
// ChildNodeInput
/////////////////////////////////////////////////////////////////////////////

/// Input values to create a new [ChildNode] as a serializable struct
#[derive(derive_more::Debug, Clone, Default, serde::Serialize, serde::Deserialize)]
pub struct ChildNodeInput {
    pub as_owner: bool,
    pub can_edit_itos: bool,
    pub can_post_cotonomas: bool,
}

impl ChildNodeInput {
    pub fn as_owner() -> ChildNodeInput {
        ChildNodeInput {
            as_owner: true,
            can_edit_itos: true,
            can_post_cotonomas: true,
        }
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
    pub can_edit_itos: Option<bool>,

    #[new(default)]
    pub can_post_cotonomas: Option<bool>,
}
