//! ParentNode related operations

use std::ops::DerefMut;

use chrono::NaiveDateTime;
use diesel::prelude::*;

use crate::{
    db::{error::*, op::*},
    models::{
        node::{
            parent::{NewParentNode, ParentNode},
            Node,
        },
        Id,
    },
    schema::parent_nodes,
};

/// Returns a [ParentNode] by its ID.
pub(crate) fn get<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Option<ParentNode>> + '_ {
    read_op(move |conn| {
        parent_nodes::table
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

/// Returns a [ParentNode] by its ID or a [DatabaseError::EntityNotFound].
pub(crate) fn try_get<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Result<ParentNode, DatabaseError>> + '_ {
    get(id).map(|opt| {
        opt.ok_or(DatabaseError::not_found(
            EntityKind::ParentNode,
            "node_id",
            *id,
        ))
    })
}

/// Returns all [ParentNode]s in arbitrary order.
pub(crate) fn all<Conn: AsReadableConn>() -> impl Operation<Conn, Vec<ParentNode>> {
    read_op(move |conn| {
        parent_nodes::table
            .load::<ParentNode>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn get_by_node_ids<Conn: AsReadableConn>(
    node_ids: &Vec<Id<Node>>,
) -> impl Operation<Conn, Vec<ParentNode>> + '_ {
    read_op(move |conn| {
        parent_nodes::table
            .filter(parent_nodes::node_id.eq_any(node_ids))
            .load::<ParentNode>(conn)
            .map_err(anyhow::Error::from)
    })
}

/// Inserts a new parent node represented as a [NewParentNode].
pub(super) fn insert<'a>(
    new_parent_node: &'a NewParentNode<'a>,
) -> impl Operation<WritableConn, ParentNode> + 'a {
    write_op(move |conn| {
        diesel::insert_into(parent_nodes::table)
            .values(new_parent_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

/// Updates a parent node row with a [ParentNode].
pub(crate) fn update(parent_node: &ParentNode) -> impl Operation<WritableConn, ParentNode> + '_ {
    write_op(move |conn| {
        diesel::update(parent_node)
            .set(parent_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(super) fn set_forked(id: &Id<Node>) -> impl Operation<WritableConn, ParentNode> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let mut parent = try_get(id).run(ctx)??;
        parent.forked = true;
        parent = update(&parent).run(ctx)?;
        Ok(parent)
    })
}

/// Updates [parent_nodes::changes_received] with a number that must be the current value + 1.
/// If the `incremented_number` is not an expected value, `Err(NotFound)` will be returned.
pub(crate) fn increment_changes_received(
    id: &Id<Node>,
    incremented_number: i64,
    received_at: Option<NaiveDateTime>,
) -> impl Operation<WritableConn, ParentNode> + '_ {
    let received_at = received_at.unwrap_or(crate::current_datetime());
    write_op(move |conn| {
        diesel::update(parent_nodes::table)
            .filter(parent_nodes::node_id.eq(id))
            // ensure the `number` is +1 increment
            .filter(parent_nodes::changes_received.eq(incremented_number - 1))
            .set((
                parent_nodes::changes_received.eq(incremented_number),
                parent_nodes::last_change_received_at.eq(Some(received_at)),
            ))
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}
