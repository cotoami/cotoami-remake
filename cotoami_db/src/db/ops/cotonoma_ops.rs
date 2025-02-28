//! Cotonoma related operations

use std::{collections::HashMap, ops::DerefMut};

use chrono::NaiveDateTime;
use diesel::prelude::*;
use validator::Validate;

use super::{coto_ops, Page};
use crate::{
    db::{error::*, op::*, ops::escape_like_pattern},
    models::{
        coto::{Coto, NewCoto},
        cotonoma::{Cotonoma, CotonomaInput, NewCotonoma, UpdateCotonoma},
        node::Node,
        Id,
    },
    schema::{cotonomas, cotos},
};

pub(crate) fn contains<Conn: AsReadableConn>(id: &Id<Cotonoma>) -> impl Operation<Conn, bool> + '_ {
    read_op(move |conn| {
        let count: i64 = cotonomas::table
            .select(diesel::dsl::count_star())
            .filter(cotonomas::uuid.eq(id))
            .first(conn)?;
        Ok(count > 0)
    })
}

pub(crate) fn get<Conn: AsReadableConn>(
    id: &Id<Cotonoma>,
) -> impl Operation<Conn, Option<Cotonoma>> + '_ {
    read_op(move |conn| {
        cotonomas::table
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn try_get<Conn: AsReadableConn>(
    id: &Id<Cotonoma>,
) -> impl Operation<Conn, Result<Cotonoma, DatabaseError>> + '_ {
    get(id).map(|opt| opt.ok_or(DatabaseError::not_found(EntityKind::Cotonoma, *id)))
}

pub(crate) fn pair<Conn: AsReadableConn>(
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

pub(crate) fn try_get_pair<Conn: AsReadableConn>(
    id: &Id<Cotonoma>,
) -> impl Operation<Conn, Result<(Cotonoma, Coto), DatabaseError>> + '_ {
    pair(id).map(|opt| opt.ok_or(DatabaseError::not_found(EntityKind::Cotonoma, *id)))
}

pub(crate) fn get_by_coto_id<Conn: AsReadableConn>(
    id: &Id<Coto>,
) -> impl Operation<Conn, Option<(Cotonoma, Coto)>> + '_ {
    read_op(move |conn| {
        cotonomas::table
            .inner_join(cotos::table)
            .filter(cotonomas::coto_id.eq(id))
            .select((Cotonoma::as_select(), Coto::as_select()))
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn try_get_by_coto_id<Conn: AsReadableConn>(
    id: &Id<Coto>,
) -> impl Operation<Conn, Result<(Cotonoma, Coto), DatabaseError>> + '_ {
    get_by_coto_id(id)
        .map(move |opt| opt.ok_or(DatabaseError::not_found(EntityKind::Cotonoma, *id)))
}

pub(crate) fn get_by_name<'a, Conn: AsReadableConn>(
    name: &'a str,
    node_id: &'a Id<Node>,
) -> impl Operation<Conn, Option<(Cotonoma, Coto)>> + 'a {
    read_op(move |conn| {
        cotonomas::table
            .inner_join(cotos::table)
            .filter(cotonomas::name.eq(name))
            .filter(cotonomas::node_id.eq(node_id))
            .select((Cotonoma::as_select(), Coto::as_select()))
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn try_get_by_name<'a, Conn: AsReadableConn>(
    name: &'a str,
    node_id: &'a Id<Node>,
) -> impl Operation<Conn, Result<(Cotonoma, Coto), DatabaseError>> + 'a {
    get_by_name(name, node_id)
        .map(move |opt| opt.ok_or(DatabaseError::not_found(EntityKind::Cotonoma, name)))
}

pub(crate) fn search_by_prefix<Conn: AsReadableConn>(
    prefix: &str,
    node_ids: Option<Vec<Id<Node>>>,
    limit: i64,
) -> impl Operation<Conn, Vec<Cotonoma>> + '_ {
    read_op(move |conn| {
        // Search by exact match first
        let query = cotonomas::table
            .filter(cotonomas::name.eq(prefix))
            .order(cotonomas::updated_at.desc())
            .limit(limit)
            .into_boxed();
        let mut exact_matches: Vec<Cotonoma> = if let Some(ref node_ids) = node_ids {
            query.filter(cotonomas::node_id.eq_any(node_ids))
        } else {
            query
        }
        .load(conn)?;

        if exact_matches.len() as i64 == limit {
            Ok(exact_matches)
        } else {
            // Then, search by prefix
            let prefix = escape_like_pattern(prefix, '\\');
            let query = cotonomas::table
                .filter(cotonomas::name.like(format!("{prefix}_%")).escape('\\'))
                .order(cotonomas::updated_at.desc())
                .limit(limit - exact_matches.len() as i64)
                .into_boxed();
            let mut prefix_matches = if let Some(ref node_ids) = node_ids {
                query.filter(cotonomas::node_id.eq_any(node_ids))
            } else {
                query
            }
            .load(conn)?;
            exact_matches.append(&mut prefix_matches);
            Ok(exact_matches)
        }
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
        // Sort the results in order of the `ids` param.
        let cotonomas = ids.iter().filter_map(|id| map.remove(id)).collect();
        Ok(cotonomas)
    })
}

pub(crate) fn get_pairs_by_ids<Conn: AsReadableConn>(
    ids: Vec<Id<Cotonoma>>,
) -> impl Operation<Conn, Vec<(Cotonoma, Coto)>> {
    read_op(move |conn| {
        let mut map: HashMap<Id<Cotonoma>, (Cotonoma, Coto)> = cotonomas::table
            .inner_join(cotos::table)
            .filter(cotonomas::uuid.eq_any(&ids))
            .select((Cotonoma::as_select(), Coto::as_select()))
            .load::<(Cotonoma, Coto)>(conn)?
            .into_iter()
            .map(|pair| (pair.0.uuid, pair))
            .collect();
        // Sort the results in order of the `ids` param.
        let cotonomas = ids.iter().filter_map(|id| map.remove(id)).collect();
        Ok(cotonomas)
    })
}

pub(crate) fn get_by_coto_ids<Conn: AsReadableConn>(
    ids: Vec<Id<Coto>>,
) -> impl Operation<Conn, Vec<Cotonoma>> {
    read_op(move |conn| {
        let mut map: HashMap<Id<Coto>, Cotonoma> = cotonomas::table
            .filter(cotonomas::coto_id.eq_any(&ids))
            .load::<Cotonoma>(conn)?
            .into_iter()
            .map(|c| (c.coto_id, c))
            .collect();
        // Sort the results in order of the `ids` param.
        let cotonomas = ids.iter().filter_map(|id| map.remove(id)).collect();
        Ok(cotonomas)
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

pub(crate) fn recently_updated<Conn: AsReadableConn>(
    node_id: Option<&Id<Node>>,
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Page<Cotonoma>> + '_ {
    read_op(move |conn| {
        super::paginate(conn, page_size, page_index, || {
            let mut query = cotonomas::table.into_boxed();
            if let Some(id) = node_id {
                query = query.filter(cotonomas::node_id.eq(id));
            }
            query.order((
                cotonomas::updated_at.desc(),
                // When a cotonoma has been posted, the updated_at timestamps of
                // the super/sub cotonoma will become the same, in the case,
                // the new(sub) cotonoma should be should precede in a recent list.
                cotonomas::created_at.desc(),
            ))
        })
    })
}

pub(crate) fn subs<Conn: AsReadableConn>(
    id: &Id<Cotonoma>,
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Page<Cotonoma>> + '_ {
    composite_op::<Conn, _, _>(move |ctx| {
        // Recently updated cotonoma-cotos including reposts.
        let cotonoma_cotos: Page<Coto> =
            super::paginate(ctx.conn().readable(), page_size, page_index, || {
                cotos::table
                    .filter(cotos::is_cotonoma.eq(true))
                    .filter(cotos::posted_in_id.eq(id))
                    .order(cotos::updated_at.desc())
            })?;

        // Collect the coto IDs of the sub cotonomas.
        // There are two kinds of sub cotonoma: "originally posted" and "reposted".
        // The coto ID of a reposted cotonoma should be taken from `repost_of_id`.
        let coto_ids: Vec<Id<Coto>> = cotonoma_cotos
            .rows
            .iter()
            .map(|coto| coto.repost_of_id.unwrap_or(coto.uuid))
            .collect();
        let cotonomas = get_by_coto_ids(coto_ids).run(ctx)?;

        Ok(Page {
            rows: cotonomas,
            size: cotonoma_cotos.size,
            index: cotonoma_cotos.index,
            total_rows: cotonoma_cotos.total_rows,
        })
    })
}

pub(crate) fn create_root<'a>(
    node_id: &'a Id<Node>,
    name: &'a str,
) -> impl Operation<WritableConn, (Cotonoma, Coto)> + 'a {
    composite_op::<WritableConn, _, _>(|ctx| {
        let new_coto = NewCoto::new_root_cotonoma(node_id, name)?;
        let (inserted_coto, _) = coto_ops::insert(&new_coto).run(ctx)?;
        let new_cotonoma =
            NewCotonoma::new(node_id, &inserted_coto.uuid, name, inserted_coto.created_at)?;
        let inserted_cotonoma = insert(&new_cotonoma).run(ctx)?;
        Ok((inserted_cotonoma, inserted_coto))
    })
}

pub(crate) fn create<'a>(
    node_id: &'a Id<Node>,
    posted_in_id: &'a Id<Cotonoma>,
    posted_by_id: &'a Id<Node>,
    input: &'a CotonomaInput<'a>,
) -> impl Operation<WritableConn, (Cotonoma, Coto)> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let new_coto = NewCoto::new_cotonoma(node_id, posted_in_id, posted_by_id, input)?;
        let (inserted_coto, _) = coto_ops::insert(&new_coto).run(ctx)?;
        let new_cotonoma = NewCotonoma::new(
            node_id,
            &inserted_coto.uuid,
            input.name.as_ref(),
            inserted_coto.created_at,
        )?;
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

pub(crate) fn rename<'a>(
    id: &'a Id<Cotonoma>,
    name: &'a str,
    updated_at: Option<NaiveDateTime>,
) -> impl Operation<WritableConn, (Cotonoma, Coto)> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let updated_at = updated_at.unwrap_or(crate::current_datetime());
        let (cotonoma, coto) = try_get_pair(id).run(ctx)??;

        // Update coto
        let mut coto = coto.to_update();
        coto.summary = Some(Some(name));
        coto.updated_at = updated_at;
        let coto_updated = coto_ops::update(&coto).run(ctx)?;

        // Update cotonoma
        let mut cotonoma = cotonoma.to_update();
        cotonoma.name = Some(name);
        cotonoma.updated_at = updated_at;
        let cotonoma_updated = update(&cotonoma).run(ctx)?;

        Ok((cotonoma_updated, coto_updated))
    })
}

pub(crate) fn update_timestamp(
    id: &Id<Cotonoma>,
    updated_at: NaiveDateTime,
) -> impl Operation<WritableConn, usize> + '_ {
    write_op(move |conn| {
        diesel::update(cotonomas::table)
            .filter(cotonomas::uuid.eq(id))
            .set(cotonomas::updated_at.eq(updated_at))
            .execute(conn.deref_mut())
            .map_err(anyhow::Error::from)
        // NOTE: cotos::updated_at will also be updated by the trigger `cotonomas_cotos_sync`.
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
