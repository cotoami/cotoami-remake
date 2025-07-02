//! Node related operations

use std::{collections::HashMap, ops::DerefMut};

use anyhow::bail;
use chrono::NaiveDateTime;
use diesel::prelude::*;
use validator::Validate;

use crate::{
    db::{error::*, op::*, ops::cotonoma_ops},
    models::{
        coto::Coto,
        cotonoma::Cotonoma,
        node::{NewNode, Node, UpdateNode},
        Id,
    },
    schema::nodes,
};

pub(crate) fn get<Conn: AsReadableConn>(id: &Id<Node>) -> impl Operation<Conn, Option<Node>> + '_ {
    read_op(move |conn| {
        nodes::table
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn try_get<Conn: AsReadableConn>(
    id: &Id<Node>,
) -> impl Operation<Conn, Result<Node, DatabaseError>> + '_ {
    get(id).map(|opt| opt.ok_or(DatabaseError::not_found(EntityKind::Node, *id)))
}

pub(crate) fn all<Conn: AsReadableConn>() -> impl Operation<Conn, Vec<Node>> {
    read_op(move |conn| {
        nodes::table
            .order(nodes::rowid.asc())
            .load::<Node>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn map_from_ids<Conn: AsReadableConn>(
    ids: &[Id<Node>],
) -> impl Operation<Conn, HashMap<Id<Node>, Node>> + '_ {
    read_op(move |conn| {
        let map: HashMap<Id<Node>, Node> = nodes::table
            .filter(nodes::uuid.eq_any(ids))
            .load::<Node>(conn)?
            .into_iter()
            .map(|c| (c.uuid, c))
            .collect();
        Ok(map)
    })
}

pub(crate) fn root_cotonoma_ids<Conn: AsReadableConn>() -> impl Operation<Conn, Vec<Id<Cotonoma>>> {
    read_op(move |conn| {
        let ids = nodes::table
            .filter(nodes::root_cotonoma_id.is_not_null())
            .select(nodes::root_cotonoma_id)
            .load::<Option<Id<Cotonoma>>>(conn)?;
        Ok(ids.into_iter().flatten().collect())
    })
}

pub(crate) fn insert<'a>(new_node: &'a NewNode<'a>) -> impl Operation<WritableConn, Node> + 'a {
    write_op(move |conn| {
        new_node.validate()?;
        diesel::insert_into(nodes::table)
            .values(new_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn get_or_insert_placeholder(id: &Id<Node>) -> impl Operation<WritableConn, Node> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        if let Some(node) = get(id).run(ctx)? {
            Ok(node)
        } else {
            insert(&NewNode::new_placeholder(*id)).run(ctx)
        }
    })
}

pub(crate) fn update<'a>(update_node: &'a UpdateNode) -> impl Operation<WritableConn, Node> + 'a {
    write_op(move |conn| {
        update_node.validate()?;
        diesel::update(update_node)
            .set(update_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

/// Renames the specified node.
///
/// The name of the root cotonoma will be also updated to the same name.
/// If the node doesn't have a root cotonoma, an error will be returned.
///
/// If the `updated_at` has some value, it will be used to set
/// [Cotonoma::updated_at] when updating it.
pub(crate) fn rename<'a>(
    id: &'a Id<Node>,
    name: &'a str,
    updated_at: Option<NaiveDateTime>,
) -> impl Operation<WritableConn, Node> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let node = set_name(id, name).run(ctx)?;
        if let Some(cotonoma_id) = node.root_cotonoma_id {
            cotonoma_ops::rename(&cotonoma_id, name, updated_at).run(ctx)?;
            Ok(node)
        } else {
            bail!("A node without a root cotonoma cannot be renamed.");
        }
    })
}

fn set_name<'a>(id: &'a Id<Node>, name: &'a str) -> impl Operation<WritableConn, Node> + 'a {
    write_op(move |conn| {
        diesel::update(nodes::table)
            .filter(nodes::uuid.eq(id))
            .set((nodes::name.eq(name), nodes::version.eq(nodes::version + 1)))
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn set_icon<'a>(
    id: &'a Id<Node>,
    icon: &'a [u8],
) -> impl Operation<WritableConn, Node> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let node = try_get(id).run(ctx)??;
        let mut update_node = node.to_update(); // incremented the node version
        update_node.set_icon(icon)?;
        update(&update_node).run(ctx)
    })
}

/// Updates the `root_cotonoma_id` of the specified node.
///
/// The node name will be updated to the name of the new root cotonoma.
pub(crate) fn set_root_cotonoma<'a>(
    id: &'a Id<Node>,
    cotonoma_id: &'a Id<Cotonoma>,
) -> impl Operation<WritableConn, Node> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let cotonoma = cotonoma_ops::try_get(cotonoma_id).run(ctx)??;
        let node = try_get(id).run(ctx)??;
        let mut update_node = node.to_update();
        update_node.name = Some(&cotonoma.name);
        update_node.root_cotonoma_id = Some(Some(&cotonoma.uuid));
        update(&update_node).run(ctx)
    })
}

pub(crate) fn create_root_cotonoma<'a>(
    node_id: &'a Id<Node>,
    name: &'a str,
) -> impl Operation<WritableConn, (Node, Cotonoma, Coto)> + 'a {
    composite_op::<WritableConn, _, _>(|ctx| {
        let (cotonoma, coto) = cotonoma_ops::create_root(node_id, name).run(ctx)?;
        let node = set_root_cotonoma(node_id, &cotonoma.uuid).run(ctx)?;
        Ok((node, cotonoma, coto))
    })
}

/// Upserting a node is an idempotent operation that inserts or updates a node
/// based on the given [Node] data.
///
/// The update will be done only when the ID of the passed data already exists
/// in the local database and the version of it is larger than the existing one.
pub(crate) fn upsert(node: &Node) -> impl Operation<WritableConn, Option<Node>> + '_ {
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
