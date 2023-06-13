//! Coto related operations

use super::Paginated;
use crate::db::op::*;
use crate::models::coto::{Coto, Cotonoma, NewCoto, UpdateCoto};
use crate::models::node::Node;
use crate::models::Id;
use diesel::prelude::*;
use std::ops::DerefMut;
use validator::Validate;

pub fn get(coto_id: &Id<Coto>) -> impl ReadOperation<Option<Coto>> + '_ {
    use crate::schema::cotos::dsl::*;
    read_op(move |conn| {
        cotos
            .find(coto_id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn recent<'a>(
    node_id: Option<&'a Id<Node>>,
    posted_in_id: Option<&'a Id<Cotonoma>>,
    page_size: i64,
    page_index: i64,
) -> impl ReadOperation<Paginated<Coto>> + 'a {
    use crate::schema::cotos;
    read_op(move |conn| {
        super::paginate(conn, page_size, page_index, || {
            let mut query = cotos::table.into_boxed();
            if let Some(id) = node_id {
                query = query.filter(cotos::node_id.eq(id));
            }
            if let Some(id) = posted_in_id {
                query = query.filter(cotos::posted_in_id.eq(id));
            }
            query.order(cotos::rowid.desc())
        })
    })
}

pub fn insert<'a>(new_coto: &'a NewCoto<'a>) -> impl Operation<WritableConnection, Coto> + 'a {
    use crate::schema::cotos::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(cotos)
            .values(new_coto)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn update<'a>(update_coto: &'a UpdateCoto) -> impl Operation<WritableConnection, Coto> + 'a {
    write_op(move |conn| {
        update_coto.validate()?;
        diesel::update(update_coto)
            .set(update_coto)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn delete(coto_id: &Id<Coto>) -> impl Operation<WritableConnection, bool> + '_ {
    use crate::schema::cotos::dsl::*;
    write_op(move |conn| {
        // The links connected to this coto will be also deleted by FOREIGN KEY ON DELETE CASCADE.
        // If it is a cotonoma, the corresponding cotonoma row will be also deleted by
        // FOREIGN KEY ON DELETE CASCADE.
        let affected = diesel::delete(cotos.find(coto_id)).execute(conn.deref_mut())?;
        Ok(affected > 0)
    })
}
