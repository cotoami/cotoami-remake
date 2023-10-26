//! ChildNode related operations

use std::ops::DerefMut;

use diesel::prelude::*;

use super::Paginated;
use crate::{
    db::{error::*, op::*},
    models::{
        node::{
            child::{ChildNode, NewChildNode},
            Node,
        },
        Id,
    },
    schema::{child_nodes, nodes},
};

pub fn all_pairs<Conn: AsReadableConn>() -> impl Operation<Conn, Vec<(ChildNode, Node)>> {
    read_op(move |conn| {
        child_nodes::table
            .inner_join(nodes::table)
            .select((ChildNode::as_select(), Node::as_select()))
            .order(child_nodes::created_at.desc())
            .load::<(ChildNode, Node)>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub fn recent_pairs<'a, Conn: AsReadableConn>(
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Paginated<(ChildNode, Node)>> + 'a {
    read_op(move |conn| {
        super::paginate(conn, page_size, page_index, || {
            child_nodes::table
                .inner_join(nodes::table)
                .select((ChildNode::as_select(), Node::as_select()))
                .order(child_nodes::created_at.desc())
        })
    })
}

pub fn get<Conn: AsReadableConn>(id: &Id<Node>) -> impl Operation<Conn, Option<ChildNode>> + '_ {
    read_op(move |conn| {
        child_nodes::table
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn get_or_err<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Result<ChildNode, DatabaseError>> + '_ {
    get(id).map(|opt| opt.ok_or(DatabaseError::not_found(EntityKind::ChildNode, *id)))
}

pub fn get_pair<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Option<(ChildNode, Node)>> + '_ {
    read_op(move |conn| {
        child_nodes::table
            .find(id)
            .inner_join(nodes::table)
            .select((ChildNode::as_select(), Node::as_select()))
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn get_pair_or_err<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Result<(ChildNode, Node), DatabaseError>> + '_ {
    get_pair(id).map(|n| n.ok_or(DatabaseError::not_found(EntityKind::ChildNode, *id)))
}

pub fn get_by_session_token<Conn: AsReadableConn>(
    token: &str,
) -> impl Operation<Conn, Option<ChildNode>> + '_ {
    read_op(move |conn| {
        child_nodes::table
            .filter(child_nodes::session_token.eq(token))
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn insert<'a>(
    new_child_node: &'a NewChildNode<'a>,
) -> impl Operation<WritableConn, ChildNode> + 'a {
    write_op(move |conn| {
        diesel::insert_into(child_nodes::table)
            .values(new_child_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn update(child_node: &ChildNode) -> impl Operation<WritableConn, ChildNode> + '_ {
    write_op(move |conn| {
        diesel::update(child_node)
            .set(child_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}
