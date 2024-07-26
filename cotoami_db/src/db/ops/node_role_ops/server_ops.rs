//! ServerNode related operations

use std::ops::DerefMut;

use diesel::prelude::*;

use crate::{
    db::{error::*, op::*},
    models::{
        node::{
            server::{EncryptedPassword, NewServerNode, ServerNode, UpdateServerNode},
            Node,
        },
        Id,
    },
    schema::{nodes, server_nodes},
};

/// Returns a [ServerNode] by its ID.
pub(crate) fn get<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Option<ServerNode>> + '_ {
    read_op(move |conn| {
        server_nodes::table
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

/// Returns a [ServerNode] by its ID or a [DatabaseError::EntityNotFound].
pub(crate) fn try_get<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Result<ServerNode, DatabaseError>> + '_ {
    get(id).map(|opt| {
        opt.ok_or(DatabaseError::not_found(
            EntityKind::ServerNode,
            "node_id",
            *id,
        ))
    })
}

/// Returns all [ServerNode]/[Node] pairs in arbitrary order.
pub(crate) fn all_pairs<Conn: AsReadableConn>() -> impl Operation<Conn, Vec<(ServerNode, Node)>> {
    read_op(move |conn| {
        server_nodes::table
            .inner_join(nodes::table)
            .select((ServerNode::as_select(), Node::as_select()))
            .load::<(ServerNode, Node)>(conn)
            .map_err(anyhow::Error::from)
    })
}

/// Inserts a new server node represented as a [NewServerNode].
pub(super) fn insert<'a>(
    new_server: &'a NewServerNode<'a>,
) -> impl Operation<WritableConn, ServerNode> + 'a {
    write_op(move |conn| {
        diesel::insert_into(server_nodes::table)
            .values(new_server)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

/// Updates a server node row with a [ServerNode].
pub(crate) fn update<'a>(
    update_server: &'a UpdateServerNode,
) -> impl Operation<WritableConn, ServerNode> + 'a {
    write_op(move |conn| {
        diesel::update(update_server)
            .set(update_server)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(super) fn set_disabled(
    id: &Id<Node>,
    disabled: bool,
) -> impl Operation<WritableConn, ServerNode> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let mut update_server = UpdateServerNode::new(id);
        update_server.disabled = Some(disabled);
        let server_updated = update(&update_server).run(ctx)?;
        Ok(server_updated)
    })
}

pub(crate) fn reencrypt_all_passwords<'a>(
    new_encryption_password: &'a str,
    old_encryption_password: &'a str,
) -> impl Operation<WritableConn, ()> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        for (server, _) in all_pairs().run(ctx)? {
            if let Some(password) = server.password(old_encryption_password)? {
                let mut update_server = server.to_update();
                update_server.set_password(&password, new_encryption_password)?;
                update(&update_server).run(ctx)?;
            }
        }
        Ok(())
    })
}

/// Clears the password of every server node in this database.
pub(crate) fn clear_all_passwords() -> impl Operation<WritableConn, usize> {
    write_op(move |conn| {
        diesel::update(server_nodes::table)
            .set(server_nodes::encrypted_password.eq(None::<EncryptedPassword>))
            .execute(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}
