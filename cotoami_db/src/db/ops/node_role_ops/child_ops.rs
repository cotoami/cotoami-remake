//! ChildNode related operations

use std::ops::DerefMut;

use diesel::prelude::*;

use crate::{
    db::{error::*, op::*, ops, ops::Paginated},
    models::{
        node::{
            child::{ChildNode, NewChildNode},
            Node,
        },
        Id,
    },
    schema::{child_nodes, nodes},
};

/// Returns a [ChildNode] by its ID.
pub(crate) fn get<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Option<ChildNode>> + '_ {
    read_op(move |conn| {
        child_nodes::table
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

/// Returns a [ChildNode] by its ID or a [DatabaseError::EntityNotFound].
pub(crate) fn try_get<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Result<ChildNode, DatabaseError>> + '_ {
    get(id).map(|opt| opt.ok_or(DatabaseError::not_found(EntityKind::ChildNode, "uuid", *id)))
}

pub(crate) fn get_by_node_ids<Conn: AsReadableConn>(
    node_ids: &Vec<Id<Node>>,
) -> impl Operation<Conn, Vec<ChildNode>> + '_ {
    read_op(move |conn| {
        child_nodes::table
            .filter(child_nodes::node_id.eq_any(node_ids))
            .load::<ChildNode>(conn)
            .map_err(anyhow::Error::from)
    })
}

/// Returns paginated results of recently inserted [ChildNode]s with their [Node]s.
pub(crate) fn recent_pairs<'a, Conn: AsReadableConn>(
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Paginated<(ChildNode, Node)>> + 'a {
    read_op(move |conn| {
        ops::paginate(conn, page_size, page_index, || {
            child_nodes::table
                .inner_join(nodes::table)
                .select((ChildNode::as_select(), Node::as_select()))
                .order(child_nodes::created_at.desc())
        })
    })
}

/// Inserts a new child node represented as a [NewChildNode].
pub(super) fn insert<'a>(
    new_child_node: &'a NewChildNode<'a>,
) -> impl Operation<WritableConn, ChildNode> + 'a {
    write_op(move |conn| {
        diesel::insert_into(child_nodes::table)
            .values(new_child_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

/// Updates a child node row with a [ChildNode].
pub(crate) fn update(child_node: &ChildNode) -> impl Operation<WritableConn, ChildNode> + '_ {
    write_op(move |conn| {
        diesel::update(child_node)
            .set(child_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn edit(
    id: &Id<Node>,
    as_owner: bool,
    can_edit_links: bool,
) -> impl Operation<WritableConn, ChildNode> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let mut child = try_get(id).run(ctx)??;
        child.as_owner = as_owner;
        child.can_edit_links = can_edit_links;
        child = update(&child).run(ctx)?;
        Ok(child)
    })
}
