//! Node related operations

use crate::db::op::*;
use crate::models::node::{NewNode, Node, UpdateNode};
use crate::models::Id;
use diesel::prelude::*;
use std::ops::DerefMut;
use validator::Validate;

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

pub fn local() -> impl ReadOperation<Option<Node>> {
    use crate::schema::nodes::dsl::*;
    read_op(move |conn| {
        nodes
            .filter(rowid.eq(Node::ROWID_FOR_LOCAL))
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn insert<'a>(new_node: &'a NewNode<'a>) -> impl Operation<WritableConnection, Node> + 'a {
    use crate::schema::nodes::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(nodes)
            .values(new_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn create_local<'a>(
    name: &'a str,
    password: Option<&'a str>,
) -> impl Operation<WritableConnection, Node> + 'a {
    composite_op::<WritableConnection, _, _>(move |ctx| {
        insert(&NewNode::new_local(name, password)?).run(ctx)
    })
}

pub fn update<'a>(update_node: &'a UpdateNode) -> impl Operation<WritableConnection, Node> + 'a {
    write_op(move |conn| {
        update_node.validate()?;
        diesel::update(update_node)
            .set(update_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn import_or_upgrade(
    received_node: &Node,
) -> impl Operation<WritableConnection, Option<Node>> + '_ {
    composite_op::<WritableConnection, _, _>(|ctx| match get(&received_node.uuid).run(ctx)? {
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
