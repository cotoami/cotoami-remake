//! LocalNode related operations

use super::node_ops;
use crate::db::op::*;
use crate::models::node::local::{LocalNode, NewLocalNode};
use crate::models::node::{NewNode, Node};
use diesel::prelude::*;
use std::ops::DerefMut;

pub fn get<Conn: AsReadableConn>() -> impl Operation<Conn, Option<(LocalNode, Node)>> {
    use crate::schema::{local_node, nodes};
    read_op(move |conn| {
        local_node::table
            .inner_join(nodes::table)
            .select((LocalNode::as_select(), Node::as_select()))
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

/// Creates a local node
///
/// This operation can be executed only once for each database.
/// A UNIQUE constraint error will be returned when a local node has already existed.
pub fn create<'a>(
    name: &'a str,
    password: Option<&'a str>,
) -> impl Operation<WritableConn, (LocalNode, Node)> + 'a {
    use crate::schema::local_node;
    composite_op::<WritableConn, _, _>(move |ctx| {
        let node = node_ops::insert(&NewNode::new(name)?).run(ctx)?;
        let new_local_node = NewLocalNode::new(&node.uuid, password)?;
        let local_node: LocalNode = diesel::insert_into(local_node::table)
            .values(new_local_node)
            .get_result(ctx.conn().deref_mut())?;
        Ok((local_node, node))
    })
}

pub fn update<'a>(local_node: &'a LocalNode) -> impl Operation<WritableConn, LocalNode> + 'a {
    write_op(move |conn| {
        diesel::update(local_node)
            .set(local_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}
