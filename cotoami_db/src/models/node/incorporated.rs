use super::Node;
use crate::models::Id;
use crate::schema::incorporated_nodes;
use chrono::NaiveDateTime;
use diesel::prelude::*;

/// A row in `incorporated_nodes` table
#[derive(Debug, Clone, PartialEq, Eq, Identifiable, Queryable)]
#[diesel(primary_key(node_id))]
pub struct IncorporatedNode {
    /// UUID of this node incorporated in this database
    pub node_id: Id<Node>,

    pub created_at: NaiveDateTime,
}

/// An `Insertable` incorporated node data
#[derive(Insertable)]
#[diesel(table_name = incorporated_nodes)]
pub struct NewIncorporatedNode<'a> {
    node_id: &'a Id<Node>,
    created_at: NaiveDateTime,
}

impl<'a> NewIncorporatedNode<'a> {
    pub fn new(node_id: &'a Id<Node>) -> Self {
        Self {
            node_id,
            created_at: crate::current_datetime(),
        }
    }
}
