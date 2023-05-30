//! Coto related operations

use crate::db::op::*;
use crate::models::coto::{Coto, NewCoto};
use crate::models::Id;
use diesel::prelude::*;
use std::ops::DerefMut;

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

pub fn insert_new<'a>(new_coto: &'a NewCoto<'a>) -> impl Operation<WritableConnection, Coto> + 'a {
    use crate::schema::cotos::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(cotos)
            .values(new_coto)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn delete<'a>(coto_id: &'a Id<Coto>) -> impl Operation<WritableConnection, ()> + 'a {
    use crate::schema::cotos::dsl::*;
    write_op(move |conn| {
        // The links connected to this node will be also deleted by FOREIGN KEY ON DELETE CASCADE
        diesel::delete(cotos.find(coto_id)).execute(conn.deref_mut())?;
        Ok(())
    })
}
