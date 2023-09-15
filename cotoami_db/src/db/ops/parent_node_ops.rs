//! ParentNode related operations

use std::ops::DerefMut;

use chrono::NaiveDateTime;
use diesel::prelude::*;

use crate::{
    db::op::*,
    models::{
        node::{
            parent::{NewParentNode, ParentNode},
            Node,
        },
        Id,
    },
};

pub fn all<Conn: AsReadableConn>() -> impl Operation<Conn, Vec<ParentNode>> {
    use crate::schema::parent_nodes::dsl::*;
    read_op(move |conn| {
        parent_nodes
            .order(created_at.desc())
            .load::<ParentNode>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub fn all_pairs<Conn: AsReadableConn>() -> impl Operation<Conn, Vec<(ParentNode, Node)>> {
    use crate::schema::{nodes, parent_nodes};
    read_op(move |conn| {
        parent_nodes::table
            .inner_join(nodes::table)
            .select((ParentNode::as_select(), Node::as_select()))
            .order(parent_nodes::created_at.desc())
            .load::<(ParentNode, Node)>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub fn insert<'a>(
    new_parent_node: &'a NewParentNode<'a>,
) -> impl Operation<WritableConn, ParentNode> + 'a {
    use crate::schema::parent_nodes::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(parent_nodes)
            .values(new_parent_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn update(parent_node: &ParentNode) -> impl Operation<WritableConn, ParentNode> + '_ {
    write_op(move |conn| {
        diesel::update(parent_node)
            .set(parent_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

/// Update `parent_nodes::changes_received` with a number that must be the current value + 1.
/// If the `incremented_number` is not an expected value, `Err(NotFound)` will be returned.
pub fn increment_changes_received(
    id: &Id<Node>,
    incremented_number: i64,
    received_at: Option<NaiveDateTime>,
) -> impl Operation<WritableConn, ParentNode> + '_ {
    use crate::schema::parent_nodes;
    let received_at = received_at.unwrap_or(crate::current_datetime());
    write_op(move |conn| {
        diesel::update(parent_nodes::table)
            .filter(
                parent_nodes::node_id
                    .eq(id)
                    // ensure the `number` is +1 increment
                    .and(parent_nodes::changes_received.eq(incremented_number - 1)),
            )
            .set((
                parent_nodes::changes_received.eq(incremented_number),
                parent_nodes::last_change_received_at.eq(Some(received_at)),
            ))
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}
