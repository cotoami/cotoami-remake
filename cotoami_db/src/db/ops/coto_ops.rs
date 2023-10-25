//! Coto related operations

use std::ops::DerefMut;

use diesel::prelude::*;
use validator::Validate;

use super::{cotonoma_ops, Paginated};
use crate::{
    db::{error::*, op::*},
    models::{
        coto::{Coto, NewCoto, UpdateCoto},
        cotonoma::Cotonoma,
        node::Node,
        Id,
    },
    schema::cotos,
};

pub fn get<Conn: AsReadableConn>(id: &Id<Coto>) -> impl Operation<Conn, Option<Coto>> + '_ {
    read_op(move |conn| {
        cotos::table
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn get_or_err<Conn: AsReadableConn>(
    id: &Id<Coto>,
) -> impl Operation<Conn, Result<Coto, DatabaseError>> + '_ {
    get(id).map(|c| c.ok_or(DatabaseError::not_found(EntityKind::Coto, *id)))
}

pub fn all<Conn: AsReadableConn>() -> impl Operation<Conn, Vec<Coto>> {
    read_op(move |conn| {
        cotos::table
            .order(cotos::rowid.asc())
            .load::<Coto>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub fn recent<'a, Conn: AsReadableConn>(
    node_id: Option<&'a Id<Node>>,
    posted_in_id: Option<&'a Id<Cotonoma>>,
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Paginated<Coto>> + 'a {
    read_op(move |conn| {
        super::paginate(conn, page_size, page_index, || {
            let mut query = cotos::table.into_boxed();
            if let Some(id) = node_id {
                query = query.filter(cotos::node_id.eq(id));
            }
            if let Some(id) = posted_in_id {
                query = query.filter(cotos::posted_in_id.eq(id));
            }
            query.order(cotos::created_at.desc())
        })
    })
}

pub fn insert<'a>(new_coto: &'a NewCoto<'a>) -> impl Operation<WritableConn, Coto> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let coto: Coto = diesel::insert_into(cotos::table)
            .values(new_coto)
            .get_result(ctx.conn().deref_mut())?;

        // Increment the `number_of_posts` in the cotonoma
        if let Some(posted_in_id) = coto.posted_in_id.as_ref() {
            cotonoma_ops::update_number_of_posts(posted_in_id, 1).run(ctx)?;
        }

        Ok(coto)
    })
}

pub fn update<'a>(update_coto: &'a UpdateCoto) -> impl Operation<WritableConn, Coto> + 'a {
    write_op(move |conn| {
        update_coto.validate()?;
        diesel::update(update_coto)
            .set(update_coto)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn delete(id: &Id<Coto>) -> impl Operation<WritableConn, bool> + '_ {
    write_op(move |conn| {
        // The links connected to this coto will be also deleted by FOREIGN KEY ON DELETE CASCADE.
        // If it is a cotonoma, the corresponding cotonoma row will be also deleted by
        // FOREIGN KEY ON DELETE CASCADE.
        let affected = diesel::delete(cotos::table.find(id)).execute(conn.deref_mut())?;
        Ok(affected > 0)
    })
}

pub fn change_owner_node<'a>(
    from: &'a Id<Node>,
    to: &'a Id<Node>,
) -> impl Operation<WritableConn, usize> + 'a {
    write_op(move |conn| {
        diesel::update(cotos::table)
            .filter(cotos::node_id.eq(from))
            .set(cotos::node_id.eq(to))
            .execute(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}
