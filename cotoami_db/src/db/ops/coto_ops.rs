//! Coto related operations

use crate::db::op::*;
use crate::models::coto::{Coto, Cotonoma, NewCoto, UpdateCoto};
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

pub fn list(posted_in_id: Option<&Id<Cotonoma>>) -> impl ReadOperation<Vec<Coto>> + '_ {
    use crate::schema::cotos;
    read_op(move |conn| {
        let mut query = cotos::table.into_boxed();
        if let Some(p) = posted_in_id {
            query = query.filter(cotos::posted_in_id.eq(p));
        }
        query
            .order(cotos::created_at.desc())
            .load::<Coto>(conn)
            .map_err(anyhow::Error::from)
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

pub fn delete(coto_id: &Id<Coto>) -> impl Operation<WritableConnection, ()> + '_ {
    use crate::schema::cotos::dsl::*;
    write_op(move |conn| {
        // The links connected to this coto will be also deleted by FOREIGN KEY ON DELETE CASCADE.
        // If it is a cotonoma, the corresponding cotonoma row will be also deleted by
        // FOREIGN KEY ON DELETE CASCADE.
        diesel::delete(cotos.find(coto_id)).execute(conn.deref_mut())?;
        Ok(())
    })
}
