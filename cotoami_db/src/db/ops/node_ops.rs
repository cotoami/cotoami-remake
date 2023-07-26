//! Node related operations

use std::ops::DerefMut;

use diesel::prelude::*;
use validator::Validate;

use crate::{
    db::op::*,
    models::{
        coto::Cotonoma,
        node::{NewNode, Node, UpdateNode},
        Id,
    },
};

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

pub fn update<'a>(update_node: &'a UpdateNode) -> impl Operation<WritableConn, Node> + 'a {
    write_op(move |conn| {
        update_node.validate()?;
        diesel::update(update_node)
            .set(update_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn update_root_cotonoma<'a>(
    id: &'a Id<Node>,
    root_cotonoma_id: &'a Id<Cotonoma>,
) -> impl Operation<WritableConn, Node> + 'a {
    use crate::schema::nodes;
    write_op(move |conn| {
        diesel::update(nodes::table)
            .filter(nodes::uuid.eq(id))
            .set((
                nodes::root_cotonoma_id.eq(root_cotonoma_id),
                nodes::version.eq(nodes::version + 1),
            ))
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

/// Importing a node is an UPSERT-like operation that inserts or updates a node based on
/// a [Node] data. The update will be done only when the version of the passed node is
/// larger than the existing one (upgrade).
pub fn import(node: &Node) -> impl Operation<WritableConn, Option<Node>> + '_ {
    composite_op::<WritableConn, _, _>(|ctx| match get(&node.uuid).run(ctx)? {
        Some(local_row) => {
            if node.version > local_row.version {
                let upgraded_node = diesel::update(&local_row)
                    .set(node.to_import())
                    .get_result(ctx.conn().deref_mut())?;
                Ok(Some(upgraded_node))
            } else {
                Ok(None)
            }
        }
        None => {
            use crate::schema::nodes::dsl::*;
            let imported_node: Node = diesel::insert_into(nodes)
                .values(node.to_import())
                .get_result(ctx.conn().deref_mut())?;
            Ok(Some(imported_node))
        }
    })
}
