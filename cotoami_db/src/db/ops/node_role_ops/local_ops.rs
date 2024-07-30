//! LocalNode related operations

use core::time::Duration;
use std::ops::DerefMut;

use anyhow::Context;
use diesel::prelude::*;
use validator::Validate;

use crate::{
    db::{error::*, op::*, ops::node_ops},
    models::node::{
        local::{LocalNode, NewLocalNode, NodeOwner, UpdateLocalNode},
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

/// Updates the local node row with a [UpdateLocalNode].
pub(crate) fn update<'a>(
    update_local: &'a UpdateLocalNode,
) -> impl Operation<WritableConn, LocalNode> + 'a {
    write_op(move |conn| {
        update_local.validate()?;
        diesel::update(update_local)
            .set(update_local)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn update_as_principal<'a>(
    owner: &'a NodeOwner,
) -> impl Operation<WritableConn, LocalNode> + 'a {
    write_op(move |conn| {
        diesel::update(owner)
            .set(owner)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn start_session<'a>(
    local_node: &'a LocalNode,
    password: &'a str,
    duration: Duration,
) -> impl Operation<WritableConn, LocalNode> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let duration = chrono::Duration::from_std(duration)?;
        let mut principal = local_node.as_principal();
        principal
            .start_session(password, duration)
            .context(DatabaseError::AuthenticationFailed)?;
        let local_node = update_as_principal(&principal).run(ctx)?;
        Ok(local_node)
    })
}

pub(crate) fn clear_session(
    local_node: &LocalNode,
) -> impl Operation<WritableConn, LocalNode> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let mut principal = local_node.as_principal();
        principal.clear_session();
        let local_node = update_as_principal(&principal).run(ctx)?;
        Ok(local_node)
    })
}
