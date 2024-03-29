//! Cotonoma related operations

use std::{collections::HashMap, ops::DerefMut};

use chrono::NaiveDateTime;
use diesel::prelude::*;
use validator::Validate;

use super::{coto_ops, Paginated};
use crate::{
    db::{error::*, op::*},
    models::{
        coto::{Coto, NewCoto},
        cotonoma::{Cotonoma, NewCotonoma, UpdateCotonoma},
        node::Node,
        Id,
    },
    schema::{cotonomas, cotos},
};

pub(crate) fn get<Conn: AsReadableConn>(
    id: &Id<Cotonoma>,
) -> impl Operation<Conn, Option<(Cotonoma, Coto)>> + '_ {
    read_op(move |conn| {
        cotonomas::table
            .inner_join(cotos::table)
            .filter(cotonomas::uuid.eq(id))
            .select((Cotonoma::as_select(), Coto::as_select()))
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn try_get<Conn: AsReadableConn>(
    id: &Id<Cotonoma>,
) -> impl Operation<Conn, Result<(Cotonoma, Coto), DatabaseError>> + '_ {
    get(id).map(|opt| opt.ok_or(DatabaseError::not_found(EntityKind::Cotonoma, *id)))
}

pub(crate) fn contains<Conn: AsReadableConn>(id: &Id<Cotonoma>) -> impl Operation<Conn, bool> + '_ {
    read_op(move |conn| {
        let count: i64 = cotonomas::table
            .select(diesel::dsl::count_star())
            .filter(cotonomas::uuid.eq(id))
            .first(conn)?;
        Ok(count > 0)
    })
}

pub(crate) fn all<Conn: AsReadableConn>() -> impl Operation<Conn, Vec<Cotonoma>> {
    read_op(move |conn| {
        cotonomas::table
            .order(cotonomas::created_at.asc())
            .load::<Cotonoma>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn get_by_ids<Conn: AsReadableConn>(
    ids: Vec<Id<Cotonoma>>,
) -> impl Operation<Conn, Vec<Cotonoma>> {
    read_op(move |conn| {
        let mut map: HashMap<Id<Cotonoma>, Cotonoma> = cotonomas::table
            .filter(cotonomas::uuid.eq_any(&ids))
            .load::<Cotonoma>(conn)?
            .into_iter()
            .map(|c| (c.uuid, c))
            .collect();
        // Sort the results in order of the `ids`.
        let cotonomas = ids.iter().map(|id| map.remove(id)).flatten().collect();
        Ok(cotonomas)
    })
}

pub(crate) fn recent<Conn: AsReadableConn>(
    node_id: Option<&Id<Node>>,
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Paginated<Cotonoma>> + '_ {
    read_op(move |conn| {
        super::paginate(conn, page_size, page_index, || {
            let mut query = cotonomas::table.into_boxed();
            if let Some(id) = node_id {
                query = query.filter(cotonomas::node_id.eq(id));
            }
            query.order(cotonomas::updated_at.desc())
        })
    })
}

pub(crate) fn subs<Conn: AsReadableConn>(
    id: &Id<Cotonoma>,
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Paginated<Cotonoma>> + '_ {
    read_op(move |conn| {
        super::paginate(conn, page_size, page_index, || {
            cotonomas::table
                .inner_join(cotos::table)
                .filter(cotos::posted_in_id.eq(id))
                // FIXME: too slow?
                // The following `LIKE` search will scan only cotonomas that have
                // non-null `reposted_in_ids`.
                .or_filter(cotos::reposted_in_ids.like(format!("%{id}%")))
                .select(Cotonoma::as_select())
                .order(cotonomas::updated_at.desc())
        })
    })
}

pub(crate) fn create_root<'a>(
    node_id: &'a Id<Node>,
    name: &'a str,
) -> impl Operation<WritableConn, (Cotonoma, Coto)> + 'a {
    composite_op::<WritableConn, _, _>(|ctx| {
        let new_coto = NewCoto::new_root_cotonoma(node_id, name)?;
        let inserted_coto = coto_ops::insert(&new_coto).run(ctx)?;
        let new_cotonoma = NewCotonoma::new(node_id, &inserted_coto.uuid, name)?;
        let inserted_cotonoma = insert(&new_cotonoma).run(ctx)?;
        Ok((inserted_cotonoma, inserted_coto))
    })
}

pub(crate) fn create<'a>(
    node_id: &'a Id<Node>,
    posted_in_id: &'a Id<Cotonoma>,
    posted_by_id: &'a Id<Node>,
    name: &'a str,
) -> impl Operation<WritableConn, (Cotonoma, Coto)> + 'a {
    composite_op::<WritableConn, _, _>(|ctx| {
        let new_coto = NewCoto::new_cotonoma(node_id, posted_in_id, posted_by_id, name)?;
        let inserted_coto = coto_ops::insert(&new_coto).run(ctx)?;
        let new_cotonoma = NewCotonoma::new(node_id, &inserted_coto.uuid, name)?;
        let inserted_cotonoma = insert(&new_cotonoma).run(ctx)?;
        Ok((inserted_cotonoma, inserted_coto))
    })
}

pub(crate) fn insert<'a>(
    new_cotonoma: &'a NewCotonoma<'a>,
) -> impl Operation<WritableConn, Cotonoma> + 'a {
    write_op(move |conn| {
        diesel::insert_into(cotonomas::table)
            .values(new_cotonoma)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn update<'a>(
    update_cotonoma: &'a UpdateCotonoma,
) -> impl Operation<WritableConn, Cotonoma> + 'a {
    write_op(move |conn| {
        update_cotonoma.validate()?;
        diesel::update(update_cotonoma)
            .set(update_cotonoma)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn delete(id: &Id<Cotonoma>) -> impl Operation<WritableConn, bool> + '_ {
    write_op(move |conn| {
        let affected = diesel::delete(cotonomas::table.find(id)).execute(conn.deref_mut())?;
        Ok(affected > 0)
    })
}

pub(crate) fn rename<'a>(
    id: &'a Id<Cotonoma>,
    name: &'a str,
    updated_at: Option<NaiveDateTime>,
) -> impl Operation<WritableConn, (Cotonoma, Coto)> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let updated_at = updated_at.unwrap_or(crate::current_datetime());
        let (cotonoma, coto) = try_get(id).run(ctx)??;

        // Update coto
        let mut coto = coto.to_update();
        coto.summary = Some(name);
        coto.updated_at = updated_at;
        let coto_updated = coto_ops::update(&coto).run(ctx)?;

        // Update cotonoma
        let mut cotonoma = cotonoma.to_update();
        cotonoma.name = name;
        cotonoma.updated_at = updated_at;
        let cotonoma_updated = update(&cotonoma).run(ctx)?;

        Ok((cotonoma_updated, coto_updated))
    })
}

pub(crate) fn change_owner_node<'a>(
    from: &'a Id<Node>,
    to: &'a Id<Node>,
) -> impl Operation<WritableConn, usize> + 'a {
    write_op(move |conn| {
        diesel::update(cotonomas::table)
            .filter(cotonomas::node_id.eq(from))
            .set(cotonomas::node_id.eq(to))
            .execute(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

// TODO: it should also update `updated_at`
pub(crate) fn update_number_of_posts(
    id: &Id<Cotonoma>,
    delta: i64,
) -> impl Operation<WritableConn, i64> + '_ {
    write_op(move |conn| {
        let cotonoma: Cotonoma = diesel::update(cotonomas::table.find(id))
            .set(cotonomas::posts.eq(cotonomas::posts + delta))
            .get_result(conn.deref_mut())?;
        Ok(cotonoma.posts)
    })
}
