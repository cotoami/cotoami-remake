use super::Node;
use crate::models::Id;
use crate::schema::child_nodes;
use chrono::NaiveDateTime;
use diesel::prelude::*;

/// A row in `child_nodes` table
#[derive(Debug, Clone, PartialEq, Eq, Identifiable, AsChangeset, Queryable)]
#[diesel(primary_key(node_id))]
pub struct ChildNode {
    /// UUID of this child node
    pub node_id: Id<Node>,

    /// Password for authentication
    pub password_hash: String,

    /// Permission to edit links in this database
    pub can_edit_links: bool,

    pub created_at: NaiveDateTime,
}

/// An `Insertable` child node data
#[derive(Insertable)]
#[diesel(table_name = child_nodes)]
pub struct NewChildNode<'a> {
    node_id: &'a Id<Node>,
    password_hash: &'a str,
    can_edit_links: bool,
    created_at: NaiveDateTime,
}

impl<'a> NewChildNode<'a> {
    pub fn new(node_id: &'a Id<Node>, password_hash: &'a str) -> Self {
        Self {
            node_id,
            password_hash,
            can_edit_links: false,
            created_at: crate::current_datetime(),
        }
    }
}
