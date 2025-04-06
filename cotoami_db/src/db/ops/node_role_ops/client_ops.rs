//! ClientNode related operations

use core::time::Duration;
use std::ops::DerefMut;

use anyhow::{ensure, Context};
use diesel::prelude::*;
use validator::Validate;

use crate::{
    db::{error::*, op::*, ops, ops::Page},
    models::{
        node::{
            client::{ClientNode, ClientNodeAsPrincipal, NewClientNode, UpdateClientNode},
            Node, Principal,
        },
        Id,
    },
    schema::{client_nodes, nodes},
};

/// Returns a [ClientNode] by its ID.
pub(crate) fn get<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Option<ClientNode>> + '_ {
    read_op(move |conn| {
        client_nodes::table
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

/// Returns a [ClientNode] by its ID or a [DatabaseError::EntityNotFound].
pub(crate) fn try_get<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Result<ClientNode, DatabaseError>> + '_ {
    get(id).map(|opt| opt.ok_or(DatabaseError::not_found(EntityKind::ClientNode, *id)))
}

/// Returns a [ClientNode] by its session token.
pub(crate) fn get_by_session_token<Conn: AsReadableConn>(
    token: &str,
) -> impl Operation<Conn, Option<ClientNode>> + '_ {
    read_op(move |conn| {
        client_nodes::table
            .filter(client_nodes::session_token.eq(token))
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

/// Returns all [ClientNode]/[Node] pairs in arbitrary order.
pub(crate) fn all_pairs<Conn: AsReadableConn>() -> impl Operation<Conn, Vec<(ClientNode, Node)>> {
    read_op(move |conn| {
        client_nodes::table
            .inner_join(nodes::table)
            .select((ClientNode::as_select(), Node::as_select()))
            .load::<(ClientNode, Node)>(conn)
            .map_err(anyhow::Error::from)
    })
}

/// Returns paginated results of recently inserted [ClientNode]s with their [Node]s.
pub(crate) fn recent_pairs<'a, Conn: AsReadableConn>(
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Page<(ClientNode, Node)>> + 'a {
    read_op(move |conn| {
        ops::paginate(conn, page_size, page_index, || {
            client_nodes::table
                .inner_join(nodes::table)
                .select((ClientNode::as_select(), Node::as_select()))
                .order((
                    client_nodes::last_session_created_at.desc(),
                    client_nodes::created_at.desc(),
                ))
        })
    })
}

/// Inserts a new client node represented as a [NewClientNode].
pub(super) fn insert<'a>(
    new_client_node: &'a NewClientNode<'a>,
) -> impl Operation<WritableConn, ClientNode> + 'a {
    write_op(move |conn| {
        diesel::insert_into(client_nodes::table)
            .values(new_client_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

/// Updates a client node row with a [UpdateClientNode].
pub(crate) fn update<'a>(
    update_client: &'a UpdateClientNode,
) -> impl Operation<WritableConn, ClientNode> + 'a {
    write_op(move |conn| {
        update_client.validate()?;
        diesel::update(update_client)
            .set(update_client)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

/// Updates a client node row with a [ClientNodeAsPrincipal].
pub(crate) fn update_as_principal<'a>(
    principal: &'a ClientNodeAsPrincipal,
) -> impl Operation<WritableConn, ClientNode> + 'a {
    write_op(move |conn| {
        diesel::update(principal)
            .set(principal)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(super) fn set_disabled(
    id: &Id<Node>,
    disabled: bool,
) -> impl Operation<WritableConn, ClientNode> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let mut update_client = UpdateClientNode::new(id);
        update_client.disabled = Some(disabled);
        let client = update(&update_client).run(ctx)?;
        Ok(client)
    })
}

pub(crate) fn start_session<'a>(
    id: &'a Id<Node>,
    password: &'a str,
    duration: Duration,
) -> impl Operation<WritableConn, ClientNode> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let client = try_get(id)
            .run(ctx)?
            // Hide a not-found error for a security reason
            .context(DatabaseError::AuthenticationFailed)?;

        // Do not allow a disabled client to start a session
        // (Hide a disabled error for a security reason)
        ensure!(!client.disabled, DatabaseError::AuthenticationFailed);

        let mut principal = client.as_principal();
        let duration = chrono::Duration::from_std(duration)?;
        principal
            .start_session(password, duration)
            .context(DatabaseError::AuthenticationFailed)?;
        let client = update_as_principal(&principal).run(ctx)?;
        Ok(client)
    })
}

pub(crate) fn clear_session(id: &Id<Node>) -> impl Operation<WritableConn, ClientNode> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let client = try_get(id).run(ctx)??;
        let mut principal = client.as_principal();
        principal.clear_session();
        let client = update_as_principal(&principal).run(ctx)?;
        Ok(client)
    })
}

pub fn change_password<'a>(
    id: &'a Id<Node>,
    new_password: &'a str,
) -> impl Operation<WritableConn, ClientNode> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let client = try_get(id).run(ctx)??;
        let mut principal = client.as_principal();
        principal.update_password(new_password)?;
        let client = update_as_principal(&principal).run(ctx)?;
        Ok(client)
    })
}
