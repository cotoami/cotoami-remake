//! Link related operations

use std::ops::DerefMut;

use diesel::prelude::*;
use validator::Validate;

use super::coto_ops;
use crate::{
    db::{error::*, op::*},
    models::{
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
    write_op(move |conn| {
        let affected = diesel::delete(links::table.find(id)).execute(conn.deref_mut())?;
        Ok(affected > 0)
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
