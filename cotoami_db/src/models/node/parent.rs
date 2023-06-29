use super::Node;
use crate::models::Id;
use crate::schema::parent_nodes;
use anyhow::Result;
use chrono::NaiveDateTime;
use diesel::prelude::*;
use validator::Validate;

/// A row in `parent_nodes` table
#[derive(Debug, Clone, Eq, PartialEq, Identifiable, AsChangeset, Queryable, Validate)]
#[diesel(primary_key(node_id))]
pub struct ParentNode {
    /// UUID of this parent node
    pub node_id: Id<Node>,

    /// URL prefix to connect to this parent node
    #[validate(url, length(max = "ParentNode::URL_PREFIX_MAX_LENGTH"))]
    pub url_prefix: String,

    pub created_at: NaiveDateTime,
}

impl ParentNode {
    // 2000 characters minus 500 for an API path after the prefix
    // cf. https://stackoverflow.com/a/417184
    pub const URL_PREFIX_MAX_LENGTH: usize = 1500;
}

/// An `Insertable` parent node data
#[derive(Insertable, Validate)]
#[diesel(table_name = parent_nodes)]
pub struct NewParentNode<'a> {
    node_id: &'a Id<Node>,
    #[validate(url, length(max = "ParentNode::URL_PREFIX_MAX_LENGTH"))]
    url_prefix: &'a str,
    created_at: NaiveDateTime,
}

impl<'a> NewParentNode<'a> {
    pub fn new(node_id: &'a Id<Node>, url_prefix: &'a str) -> Result<Self> {
        let parent_node = Self {
            node_id,
            url_prefix,
            created_at: crate::current_datetime(),
        };
        parent_node.validate()?;
        Ok(parent_node)
    }
}
