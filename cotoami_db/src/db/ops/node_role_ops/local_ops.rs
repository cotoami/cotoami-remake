//! LocalNode related operations

use core::time::Duration;
use std::ops::DerefMut;

use anyhow::Context;
use diesel::prelude::*;

use crate::{
    db::{error::*, op::*, ops::node_ops},
    models::node::{
        local::{LocalNode, NewLocalNode},
        NewNode, Node, Principal,
    },
    schema::{local_node, nodes},
};

pub(crate) fn get_pair<Conn: AsReadableConn>() -> impl Operation<Conn, Option<(LocalNode, Node)>> {
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
pub(crate) fn create<'a>(
    name: &'a str,
    password: Option<&'a str>,
) -> impl Operation<WritableConn, (LocalNode, Node)> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let node = node_ops::insert(&NewNode::new(name)?).run(ctx)?;
        let new_local_node = NewLocalNode::new(&node.uuid, password)?;
        let local_node: LocalNode = diesel::insert_into(local_node::table)
            .values(new_local_node)
            .get_result(ctx.conn().deref_mut())?;
        Ok((local_node, node))
    })
}

pub(crate) fn update(local_node: &LocalNode) -> impl Operation<WritableConn, LocalNode> + '_ {
    write_op(move |conn| {
        diesel::update(local_node)
            .set(local_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn start_session<'a>(
    local_node: &'a mut LocalNode,
    password: &'a str,
    duration: Duration,
) -> impl Operation<WritableConn, ()> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let duration = chrono::Duration::from_std(duration)?;
        local_node
            .start_session(password, duration)
            .context(DatabaseError::AuthenticationFailed)?;
        *local_node = update(&local_node).run(ctx)?;
        Ok(())
    })
}

pub(crate) fn clear_session(local_node: &mut LocalNode) -> impl Operation<WritableConn, ()> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        local_node.clear_session();
        *local_node = update(&local_node).run(ctx)?;
        Ok(())
    })
}
