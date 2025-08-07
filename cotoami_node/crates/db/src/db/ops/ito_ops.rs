//! Ito related operations

use std::ops::DerefMut;

use anyhow::{bail, ensure};
use chrono::NaiveDateTime;
use diesel::{dsl::max, prelude::*};
use tracing::debug;
use validator::Validate;

use super::Page;
use crate::{
    db::{error::*, op::*, ops::coto_ops},
    models::{coto::Coto, ito::*, node::Node, Id},
    schema::{cotos, itos},
};

pub(crate) fn get<Conn: ReadConn>(id: &Id<Ito>) -> impl Operation<Conn, Option<Ito>> + '_ {
    read_op(move |conn| {
        itos::table
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn try_get<Conn: ReadConn>(
    id: &Id<Ito>,
) -> impl Operation<Conn, Result<Ito, DatabaseError>> + '_ {
    get(id).map(|opt| opt.ok_or(DatabaseError::not_found(EntityKind::Ito, *id)))
}

pub(crate) fn incoming<Conn: ReadConn>(coto_id: &Id<Coto>) -> impl Operation<Conn, Vec<Ito>> + '_ {
    read_op(move |conn| {
        itos::table
            .filter(itos::target_coto_id.eq(coto_id))
            .load::<Ito>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn outgoing<Conn: ReadConn>(
    coto_ids: &[Id<Coto>],
) -> impl Operation<Conn, Vec<Ito>> + '_ {
    read_op(move |conn| {
        itos::table
            .filter(itos::source_coto_id.eq_any(coto_ids))
            .load::<Ito>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn siblings<'a, Conn: ReadConn>(
    source_coto_id: &'a Id<Coto>,
    node_id: Option<&'a Id<Node>>,
) -> impl Operation<Conn, Vec<Ito>> + 'a {
    read_op(move |conn| {
        let mut query = itos::table
            .filter(itos::source_coto_id.eq(source_coto_id))
            .into_boxed();
        if let Some(node_id) = node_id {
            query = query.filter(itos::node_id.eq(node_id));
        }
        query
            .order(itos::order.asc())
            .load::<Ito>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn recent<'a, Conn: ReadConn>(
    node_id: Option<&'a Id<Node>>,
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Page<Ito>> + 'a {
    read_op(move |conn| {
        super::paginate(conn, page_size, page_index, || {
            let mut query = itos::table.into_boxed();
            if let Some(node_id) = node_id {
                query = query.filter(itos::node_id.eq(node_id));
            }
            query.order(itos::created_at.desc())
        })
    })
}

pub(crate) fn determine_node<'a, Conn: ReadConn>(
    source: &'a Id<Coto>,
    target: &'a Id<Coto>,
    local_node_id: &'a Id<Node>,
) -> impl Operation<Conn, Id<Node>> + 'a {
    read_op(move |conn| {
        ensure!(
            source != target,
            "Source and target must be different cotos."
        );

        let node_ids = cotos::table
            .filter(cotos::uuid.eq_any(&[source, target]))
            .select(cotos::node_id)
            .load::<Id<Node>>(conn)?;

        if let [node_id1, node_id2] = node_ids[..] {
            Ok(Ito::determine_node(&node_id1, &node_id2, local_node_id))
        } else {
            bail!("Invalid source and target coto IDs.");
        }
    })
}

pub(crate) fn insert(mut new_ito: NewIto<'_>) -> impl Operation<WritableConn, Ito> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        new_ito.validate()?;

        // Ensure both of cotos are not reposts
        ensure!(
            !coto_ops::any_reposts_in(&[*new_ito.source_coto_id(), *new_ito.target_coto_id()])
                .run(ctx)?,
            DatabaseError::RepostsCannotBeConnected
        );

        // Deal with the order
        if let Some(order) = new_ito.order {
            ensure_space_at(new_ito.node_id(), new_ito.source_coto_id(), order).run(ctx)?;
        } else {
            let last_number = last_order_number(new_ito.source_coto_id())
                .run(ctx)?
                .unwrap_or(0);
            new_ito.order = Some(last_number + 1);
        }

        diesel::insert_into(itos::table)
            .values(new_ito)
            .get_result(ctx.conn().deref_mut())
            .map_err(anyhow::Error::from)
    })
}

fn last_order_number<Conn: ReadConn>(coto_id: &Id<Coto>) -> impl Operation<Conn, Option<i32>> + '_ {
    read_op(move |conn| {
        itos::table
            .select(max(itos::order))
            .filter(itos::source_coto_id.eq(coto_id))
            .first(conn)
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn change_order(id: &Id<Ito>, new_order: i32) -> impl Operation<WritableConn, Ito> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let ito = try_get(id).run(ctx)??;
        ensure_space_at(&ito.node_id, &ito.source_coto_id, new_order).run(ctx)?;
        diesel::update(itos::table)
            .filter(itos::uuid.eq(ito.uuid))
            .set(itos::order.eq(new_order))
            .get_result(ctx.conn().deref_mut())
            .map_err(anyhow::Error::from)
    })
}

/// Ensure the given order is available by conditionally shifting itos to the right
/// starting from the order.
fn ensure_space_at<'a>(
    node_id: &'a Id<Node>,
    coto_id: &'a Id<Coto>,
    order: i32,
) -> impl Operation<WritableConn, usize> + 'a {
    write_op(move |conn| {
        let orders_onwards: Vec<i32> = itos::table
            .select(itos::order)
            .filter(itos::node_id.eq(node_id))
            .filter(itos::source_coto_id.eq(coto_id))
            .filter(itos::order.ge(order))
            .order(itos::order.asc())
            .load::<i32>(conn.deref_mut())?;

        // Has the given `order` number already been used?
        if orders_onwards.first() == Some(&order) {
            // calculate new orders
            let mut new_orders: Vec<i32> = vec![order + 1]; // shift the first order +1
            for old_order in orders_onwards[1..].iter() {
                if new_orders.contains(old_order) {
                    new_orders.push(old_order + 1); // shift it +1 accordingly
                } else {
                    // no change needed due to missing numbers in the current orders
                    new_orders.push(*old_order);
                }
            }
            assert_eq!(new_orders.len(), orders_onwards.len());

            // apply the new orders in descending order to avoid a UNIQUE constraint error
            let mut updated = 0;
            for (old_order, new_order) in orders_onwards.iter().zip(new_orders.iter()).rev() {
                if old_order != new_order {
                    diesel::update(itos::table)
                        .filter(itos::node_id.eq(node_id))
                        .filter(itos::source_coto_id.eq(coto_id))
                        .filter(itos::order.eq(old_order))
                        .set(itos::order.eq(new_order))
                        .execute(conn.deref_mut())?;
                    updated += 1;
                }
            }
            debug!("{updated} itos moved over to make room for: {order}");
            Ok(updated)
        } else {
            Ok(0)
        }
    })
}

pub(crate) fn update<'a>(update_ito: &'a UpdateIto) -> impl Operation<WritableConn, Ito> + 'a {
    write_op(move |conn| {
        update_ito.validate()?;
        diesel::update(update_ito)
            .set(update_ito)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn edit<'a>(
    id: &'a Id<Ito>,
    diff: &'a ItoContentDiff<'a>,
    updated_at: Option<NaiveDateTime>,
) -> impl Operation<WritableConn, Ito> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let mut update_ito = UpdateIto::new(id);
        update_ito.edit_content(diff);
        update_ito.updated_at = updated_at.unwrap_or(crate::current_datetime());
        update(&update_ito).run(ctx)
    })
}

pub(crate) fn delete(id: &Id<Ito>) -> impl Operation<WritableConn, bool> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let deleted: Option<Ito> = diesel::delete(itos::table.find(id))
            .get_result(ctx.conn().deref_mut())
            .optional()?;
        Ok(deleted.is_some())
    })
}
