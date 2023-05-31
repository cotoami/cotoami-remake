//! Link related operations

use crate::db::op::*;
use crate::models::coto::{Link, NewLink, UpdateLink};
use crate::models::Id;
use diesel::prelude::*;
use std::ops::DerefMut;
use validator::Validate;

pub fn get(link_id: &Id<Link>) -> impl ReadOperation<Option<Link>> + '_ {
    use crate::schema::links::dsl::*;
    read_op(move |conn| {
        links
            .find(link_id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn insert<'a>(new_link: &'a NewLink<'a>) -> impl Operation<WritableConnection, Link> + 'a {
    use crate::schema::links::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(links)
            .values(new_link)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn update<'a>(update_link: &'a UpdateLink) -> impl Operation<WritableConnection, Link> + 'a {
    write_op(move |conn| {
        update_link.validate()?;
        diesel::update(update_link)
            .set(update_link)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn delete(link_id: &Id<Link>) -> impl Operation<WritableConnection, ()> + '_ {
    use crate::schema::links::dsl::*;
    write_op(move |conn| {
        diesel::delete(links.find(link_id)).execute(conn.deref_mut())?;
        Ok(())
    })
}
