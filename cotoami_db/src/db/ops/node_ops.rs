//! Node related operations

use crate::db::op::{
    composite_op, read_op, write_op, Operation, ReadOperation, WritableConnection,
};
use crate::models::node::{NewNode, Node};
use crate::models::Id;
use diesel::prelude::*;
use std::ops::DerefMut;

pub fn all() -> impl ReadOperation<Vec<Node>> {
    use crate::schema::nodes::dsl::*;
    read_op(move |conn| {
        nodes
            .order(rowid.asc())
            .load::<Node>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub fn get(node_id: &Id<Node>) -> impl ReadOperation<Option<Node>> + '_ {
    use crate::schema::nodes::dsl::*;
    read_op(move |conn| {
        nodes
            .find(node_id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn insert_new<'a>(new_node: &'a NewNode<'a>) -> impl Operation<WritableConnection, Node> + 'a {
    use crate::schema::nodes::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(nodes)
            .values(new_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn update(node: &Node) -> impl Operation<WritableConnection, Node> + '_ {
    write_op(move |conn| {
        diesel::update(node)
            .set(node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn import_or_upgrade(
    received_node: &Node,
) -> impl Operation<WritableConnection, Option<Node>> + '_ {
    composite_op::<WritableConnection, _, _>(move |ctx| {
        match get(&received_node.uuid).run(ctx)? {
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
        }
    })
}
