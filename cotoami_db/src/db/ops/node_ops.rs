//! Node related operations

use crate::db::op::*;
use crate::models::node::{LocalNode, NewLocalNode, NewNode, Node, UpdateNode};
use crate::models::Id;
use diesel::prelude::*;
use std::ops::DerefMut;
use validator::Validate;

pub fn get<Conn: AsReadableConn>(node_id: &Id<Node>) -> impl Operation<Conn, Option<Node>> + '_ {
    use crate::schema::nodes::dsl::*;
    read_op(move |conn| {
        nodes
            .find(node_id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn local<Conn: AsReadableConn>() -> impl Operation<Conn, Option<(LocalNode, Node)>> {
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

pub fn all<Conn: AsReadableConn>() -> impl Operation<Conn, Vec<Node>> {
    use crate::schema::nodes::dsl::*;
    read_op(move |conn| {
        nodes
            .order(rowid.asc())
            .load::<Node>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub fn insert<'a>(new_node: &'a NewNode<'a>) -> impl Operation<WritableConn, Node> + 'a {
    use crate::schema::nodes::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(nodes)
            .values(new_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

/// Creates a local node
///
/// This operation can be executed only once for each database.
/// A UNIQUE constraint error will be returned when a local node has already existed.
pub fn create_local<'a>(
    name: &'a str,
    password: Option<&'a str>,
) -> impl Operation<WritableConn, (LocalNode, Node)> + 'a {
    use crate::schema::local_node;
    composite_op::<WritableConn, _, _>(move |ctx| {
        let node = insert(&NewNode::new_local(name)?).run(ctx)?;
        let new_local_node = NewLocalNode::new(&node.uuid, password)?;
        let local_node: LocalNode = diesel::insert_into(local_node::table)
            .values(new_local_node)
            .get_result(ctx.conn().deref_mut())?;
        Ok((local_node, node))
    })
}

pub fn update<'a>(update_node: &'a UpdateNode) -> impl Operation<WritableConn, Node> + 'a {
    write_op(move |conn| {
        update_node.validate()?;
        diesel::update(update_node)
            .set(update_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn batch_import(
    received_nodes: &Vec<Node>,
) -> impl Operation<WritableConn, Vec<Option<Node>>> + '_ {
    composite_op::<WritableConn, _, _>(|ctx| {
        let mut results = Vec::new();
        for node in received_nodes.iter() {
            results.push(import_or_upgrade(&node).run(ctx)?);
        }
        Ok(results)
    })
}

fn import_or_upgrade(received_node: &Node) -> impl Operation<WritableConn, Option<Node>> + '_ {
    composite_op::<WritableConn, _, _>(|ctx| match get(&received_node.uuid).run(ctx)? {
        Some(local_row) => {
            if received_node.version > local_row.version {
                let upgraded_node = diesel::update(&local_row)
                    .set(received_node.to_import())
                    .get_result(ctx.conn().deref_mut())?;
                Ok(Some(upgraded_node))
            } else {
                Ok(None)
            }
        }
        None => {
            use crate::schema::nodes::dsl::*;
            let imported_node: Node = diesel::insert_into(nodes)
                .values(received_node.to_import())
                .get_result(ctx.conn().deref_mut())?;
            Ok(Some(imported_node))
        }
    })
}
