//! ChildNode related operations

use std::ops::DerefMut;

use diesel::prelude::*;

use crate::{
    db::{error::*, op::*},
    models::{
        node::{
            child::{ChildNode, NewChildNode},
            Node,
        },
        Id,
    },
};

pub fn get<Conn: AsReadableConn>(id: &Id<Node>) -> impl Operation<Conn, Option<ChildNode>> + '_ {
    use crate::schema::child_nodes::dsl::*;
    read_op(move |conn| {
        child_nodes
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn get_or_err<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Result<ChildNode, DatabaseError>> + '_ {
    get(id).map(|n| n.ok_or(DatabaseError::not_found(EntityKind::ChildNode, *id)))
}

pub fn get_by_session_token<Conn: AsReadableConn>(
    token: &str,
) -> impl Operation<Conn, Option<ChildNode>> + '_ {
    use crate::schema::child_nodes::dsl::*;
    read_op(move |conn| {
        child_nodes
            .filter(session_token.eq(token))
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn insert<'a>(
    new_child_node: &'a NewChildNode<'a>,
) -> impl Operation<WritableConn, ChildNode> + 'a {
    use crate::schema::child_nodes::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(child_nodes)
            .values(new_child_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn update<'a>(child_node: &'a ChildNode) -> impl Operation<WritableConn, ChildNode> + 'a {
    write_op(move |conn| {
        diesel::update(child_node)
            .set(child_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}
