//! Link related operations

use crate::db::op::*;
use crate::models::coto::{Link, NewLink, UpdateLink};
use crate::models::Id;
use chrono::NaiveDateTime;
use diesel::prelude::*;
use std::ops::DerefMut;
use validator::Validate;

pub fn get(id: &Id<Link>) -> impl ReadOperation<Option<Link>> + '_ {
    use crate::schema::links::dsl::*;
    read_op(move |conn| {
        links
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn insert<'a>(new_link: &'a NewLink<'a>) -> impl Operation<WritableConn, Link> + 'a {
    use crate::schema::links::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(links)
            .values(new_link)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
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
    use crate::schema::links::dsl::*;
    write_op(move |conn| {
        let affected = diesel::delete(links.find(id)).execute(conn.deref_mut())?;
        Ok(affected > 0)
    })
}

pub fn update_linking_phrase<'a>(
    id: &'a Id<Link>,
    linking_phrase: Option<&'a str>,
    updated_at: Option<NaiveDateTime>,
) -> impl Operation<WritableConn, Option<Link>> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let updated_at = updated_at.unwrap_or(crate::current_datetime());
        if let Some(link) = get(id).run(ctx)? {
            let mut link = link.to_update();
            link.linking_phrase = linking_phrase;
            link.updated_at = updated_at;
            let updated_link = update(&link).run(ctx)?;
            Ok(Some(updated_link))
        } else {
            Ok(None)
        }
    })
}
