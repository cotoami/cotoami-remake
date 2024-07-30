//! ChildNode related operations

use std::ops::DerefMut;

use diesel::prelude::*;
use validator::Validate;

use crate::{
    db::{error::*, op::*, ops, ops::Paginated},
    models::{
        node::{
            child::{ChildNode, NewChildNode, UpdateChildNode},
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

/// Updates a child node row with a [UpdateChildNode].
pub(crate) fn update<'a>(
    update_child: &'a UpdateChildNode,
) -> impl Operation<WritableConn, ChildNode> + 'a {
    write_op(move |conn| {
        update_child.validate()?;
        diesel::update(update_child)
            .set(update_child)
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
        let mut update_child = UpdateChildNode::new(id);
        update_child.as_owner = Some(as_owner);
        update_child.can_edit_links = Some(can_edit_links);
        let child = update(&update_child).run(ctx)?;
        Ok(child)
    })
}
