//! Link related operations

use std::ops::DerefMut;

use diesel::prelude::*;
use validator::Validate;

use super::{coto_ops, Paginated};
use crate::{
    db::{error::*, op::*},
    models::{
        cotonoma::Cotonoma,
        link::{Link, NewLink, UpdateLink},
        node::Node,
        Id,
    },
    schema::links,
};

pub fn get<Conn: AsReadableConn>(id: &Id<Link>) -> impl Operation<Conn, Option<Link>> + '_ {
    read_op(move |conn| {
        links::table
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn get_or_err<Conn: AsReadableConn>(
    id: &Id<Link>,
) -> impl Operation<Conn, Result<Link, DatabaseError>> + '_ {
    get(id).map(|opt| opt.ok_or(DatabaseError::not_found(EntityKind::Link, *id)))
}

pub fn recent<'a, Conn: AsReadableConn>(
    node_id: Option<&'a Id<Node>>,
    created_in_id: Option<&'a Id<Cotonoma>>,
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Paginated<Link>> + 'a {
    read_op(move |conn| {
        super::paginate(conn, page_size, page_index, || {
            let mut query = links::table.into_boxed();
            if let Some(id) = node_id {
                query = query.filter(links::node_id.eq(id));
            }
            if let Some(id) = created_in_id {
                query = query.filter(links::created_in_id.eq(id));
            }
            query.order(links::created_at.desc())
        })
    })
}

pub fn insert<'a>(new_link: &'a NewLink<'a>) -> impl Operation<WritableConn, Link> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let link: Link = diesel::insert_into(links::table)
            .values(new_link)
            .get_result(ctx.conn().deref_mut())?;
        coto_ops::update_number_of_outgoing_links(&link.source_coto_id, 1).run(ctx)?;
        Ok(link)
    })
}

pub fn update<'a>(update_link: &'a UpdateLink) -> impl Operation<WritableConn, Link> + 'a {
    write_op(move |conn| {
        update_link.validate()?;
        diesel::update(update_link)
            .set(update_link)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn delete(id: &Id<Link>) -> impl Operation<WritableConn, bool> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let deleted: Option<Link> = diesel::delete(links::table.find(id))
            .get_result(ctx.conn().deref_mut())
            .optional()?;

        if let Some(link) = deleted {
            coto_ops::update_number_of_outgoing_links(&link.source_coto_id, -1).run(ctx)?;
            Ok(true)
        } else {
            Ok(false)
        }
    })
}

pub fn change_owner_node<'a>(
    from: &'a Id<Node>,
    to: &'a Id<Node>,
) -> impl Operation<WritableConn, usize> + 'a {
    write_op(move |conn| {
        diesel::update(links::table)
            .filter(links::node_id.eq(from))
            .set(links::node_id.eq(to))
            .execute(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}
