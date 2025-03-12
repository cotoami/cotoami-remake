//! Coto related operations

use std::{borrow::Cow, collections::HashMap, ops::DerefMut};

use anyhow::{ensure, Context, Result};
use chrono::NaiveDateTime;
use diesel::prelude::*;
use validator::Validate;

use crate::{
    db::{
        error::*,
        op::*,
        ops::{cotonoma_ops, detect_cjk_chars, escape_like_pattern, Page},
    },
    models::{
        coto::{Coto, CotoContentDiff, NewCoto, UpdateCoto},
        cotonoma::{Cotonoma, NewCotonoma},
        node::Node,
        Geolocation, Id,
    },
    schema::cotos,
};

pub(crate) fn get<Conn: AsReadableConn>(id: &Id<Coto>) -> impl Operation<Conn, Option<Coto>> + '_ {
    read_op(move |conn| {
        cotos::table
            .find(id)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn try_get<Conn: AsReadableConn>(
    id: &Id<Coto>,
) -> impl Operation<Conn, Result<Coto, DatabaseError>> + '_ {
    get(id).map(|opt| opt.ok_or(DatabaseError::not_found(EntityKind::Coto, *id)))
}

pub(crate) fn get_original<Conn: AsReadableConn>(coto: Coto) -> impl Operation<Conn, Coto> {
    composite_op::<Conn, _, _>(move |ctx| {
        if let Some(ref repost_of_id) = coto.repost_of_id {
            try_get(repost_of_id).run(ctx)?.map_err(anyhow::Error::from)
        } else {
            Ok(coto)
        }
    })
}

pub(crate) fn contains<Conn: AsReadableConn>(id: &Id<Coto>) -> impl Operation<Conn, bool> + '_ {
    read_op(move |conn| {
        let count: i64 = cotos::table
            .select(diesel::dsl::count_star())
            .filter(cotos::uuid.eq(id))
            .first(conn)?;
        Ok(count > 0)
    })
}

pub(crate) fn any_reposts_in<'a, Conn: AsReadableConn>(
    ids: &'a [Id<Coto>],
) -> impl Operation<Conn, bool> + 'a {
    read_op(move |conn| {
        let count: i64 = cotos::table
            .select(diesel::dsl::count_star())
            .filter(cotos::uuid.eq_any(ids))
            .filter(cotos::repost_of_id.is_not_null())
            .first(conn)?;
        Ok(count > 0)
    })
}

pub(crate) fn all<Conn: AsReadableConn>() -> impl Operation<Conn, Vec<Coto>> {
    read_op(move |conn| {
        cotos::table
            .order(cotos::rowid.asc())
            .load::<Coto>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn get_by_ids<'a, Conn: AsReadableConn>(
    ids: &'a [Id<Coto>],
) -> impl Operation<Conn, Vec<Coto>> + 'a {
    read_op(move |conn| {
        let mut map: HashMap<Id<Coto>, Coto> = cotos::table
            .filter(cotos::uuid.eq_any(ids))
            .load::<Coto>(conn)?
            .into_iter()
            .map(|c| (c.uuid, c))
            .collect();
        // Sort the results in order of the `ids` param.
        let cotonomas = ids.iter().filter_map(|id| map.remove(id)).collect();
        Ok(cotonomas)
    })
}

pub(crate) fn recently_inserted<'a, Conn: AsReadableConn>(
    node_id: Option<&'a Id<Node>>,
    posted_in_id: Option<&'a Id<Cotonoma>>,
    only_cotonomas: bool,
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Page<Coto>> + 'a {
    read_op(move |conn| {
        super::paginate(conn, page_size, page_index, || {
            let mut query = cotos::table.into_boxed();
            if only_cotonomas {
                query = query.filter(cotos::is_cotonoma.eq(true));
            }
            match (node_id, posted_in_id) {
                (Some(node_id), None) => query.filter(cotos::node_id.eq(node_id)),
                (_, Some(posted_in_id)) => query.filter(cotos::posted_in_id.eq(posted_in_id)),
                _ => query,
            }
            .order(cotos::created_at.desc())
        })
    })
}

pub(crate) fn geolocated<'a, Conn: AsReadableConn>(
    node_id: Option<&'a Id<Node>>,
    posted_in_id: Option<&'a Id<Cotonoma>>,
    limit: i64,
) -> impl Operation<Conn, Vec<Coto>> + 'a {
    read_op(move |conn| {
        let geolocated_cotos = cotos::table
            .filter(cotos::longitude.is_not_null())
            .filter(cotos::latitude.is_not_null())
            .order(cotos::created_at.desc())
            .limit(limit)
            .into_boxed();

        match (node_id, posted_in_id) {
            (Some(node_id), None) => geolocated_cotos.filter(cotos::node_id.eq(node_id)),
            (_, Some(posted_in_id)) => {
                geolocated_cotos.filter(cotos::posted_in_id.eq(posted_in_id))
            }
            _ => geolocated_cotos,
        }
        .load::<Coto>(conn)
        .map_err(anyhow::Error::from)
    })
}

pub(crate) fn in_geo_bounds<'a, Conn: AsReadableConn>(
    southwest: &'a Geolocation,
    northeast: &'a Geolocation,
    limit: i64,
) -> impl Operation<Conn, Vec<Coto>> + 'a {
    read_op(move |conn| {
        cotos::table
            // search against the `cotos_lng_lat` index
            .filter(cotos::longitude.between(southwest.longitude, northeast.longitude))
            .filter(cotos::latitude.between(southwest.latitude, northeast.latitude))
            .order(cotos::created_at.desc())
            .limit(limit)
            .load::<Coto>(conn)
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn insert<'a>(
    new_coto: &'a NewCoto<'a>,
) -> impl Operation<WritableConn, (Coto, Option<Coto>)> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        new_coto.validate()?;
        let coto: Coto = diesel::insert_into(cotos::table)
            .values(new_coto)
            .get_result(ctx.conn().deref_mut())?;

        if let Some(ref posted_in_id) = coto.posted_in_id {
            // Update the cotonoma's timestamp
            cotonoma_ops::update_timestamp(posted_in_id, coto.created_at).run(ctx)?;

            // Update the orginal coto if this is a repost
            if let Some(ref repost_of_id) = coto.repost_of_id {
                let original = reposted(
                    &try_get(repost_of_id).run(ctx)??,
                    posted_in_id,
                    coto.created_at,
                )
                .run(ctx)?;
                return Ok((coto, Some(original)));
            }
        }

        Ok((coto, None))
    })
}

pub(crate) fn repost<'a>(
    id: &'a Id<Coto>,
    dest: &'a Id<Cotonoma>,
    reposted_by: &'a Id<Node>,
    reposted_at: Option<NaiveDateTime>,
) -> impl Operation<WritableConn, (Coto, Coto)> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let coto = try_get(id).run(ctx)??;
        let original = get_original(coto).run(ctx)?;
        let dest = cotonoma_ops::try_get(dest).run(ctx)??;
        let reposted_at = reposted_at.unwrap_or(crate::current_datetime());

        // Insert a repost
        let mut new_repost = NewCoto::new_repost(&original.uuid, &dest, reposted_by);
        new_repost.set_timestamp(reposted_at);
        let (repost, original) = insert(&new_repost).run(ctx)?;
        let original = original.unwrap_or_else(|| unreachable!());

        Ok((repost, original))
    })
}

pub(crate) fn update<'a>(update_coto: &'a UpdateCoto) -> impl Operation<WritableConn, Coto> + 'a {
    write_op(move |conn| {
        update_coto.validate()?;
        diesel::update(update_coto)
            .set(update_coto)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn edit<'a>(
    id: &'a Id<Coto>,
    diff: &'a CotoContentDiff<'a>,
    image_max_size: Option<u32>,
    updated_at: Option<NaiveDateTime>,
) -> impl Operation<WritableConn, Coto> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let mut update_coto = UpdateCoto::new(id);
        update_coto.edit_content(diff, image_max_size)?;
        update_coto.updated_at = updated_at.unwrap_or(crate::current_datetime());
        update(&update_coto).run(ctx)
    })
}

pub(crate) fn promote<'a>(
    id: &'a Id<Coto>,
    promoted_at: Option<NaiveDateTime>,
) -> impl Operation<WritableConn, (Cotonoma, Coto)> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let promoted_at = promoted_at.unwrap_or(crate::current_datetime());

        // Update the coto
        let coto = try_get(id).run(ctx)??;
        let mut update_coto = coto.to_promote()?;
        update_coto.updated_at = promoted_at;
        let coto = update(&update_coto).run(ctx)?;

        // Insert a cotonoma
        let new_cotonoma = NewCotonoma::new(
            &coto.node_id,
            &coto.uuid,
            coto.name_as_cotonoma().unwrap(),
            promoted_at,
        )?;
        let inserted_cotonoma = cotonoma_ops::insert(&new_cotonoma).run(ctx)?;

        Ok((inserted_cotonoma, coto))
    })
}

fn reposted<'a>(
    original: &'a Coto,
    dest: &'a Id<Cotonoma>,
    reposted_at: NaiveDateTime,
) -> impl Operation<WritableConn, Coto> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        ensure!(
            original.repost_of_id.is_none(),
            "A coto to be reposted must not be a repost."
        );
        ensure!(!original.posted_in(dest), DatabaseError::DuplicateRepost);

        let mut update_original = original.to_update();
        update_original.repost_in(*dest, &original);
        update_original.updated_at = reposted_at;
        update(&update_original).run(ctx)
    })
}

pub(crate) fn delete(
    id: &Id<Coto>,
    deleted_at: Option<NaiveDateTime>,
) -> impl Operation<WritableConn, Option<NaiveDateTime>> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let deleted_at = deleted_at.unwrap_or(crate::current_datetime());

        // There are some related entities to be deleted by FOREIGN KEY ON DELETE CASCADE:
        // 1. The reposts of the coto.
        // 2. The itos connected to the coto.
        // 3. If it is a cotonoma, the corresponding cotonoma row will be deleted.
        let deleted: Option<Coto> = diesel::delete(cotos::table.find(id))
            .get_result(ctx.conn().deref_mut())
            .optional()?;

        if let Some(coto) = deleted {
            // Update the original coto if the deleted coto is a report
            if let (Some(ref repost_of_id), Some(ref posted_in_id)) =
                (coto.repost_of_id, coto.posted_in_id)
            {
                let original = try_get(repost_of_id).run(ctx)??;
                let mut update_original = original.to_update();
                update_original.remove_reposted_in(posted_in_id, &original);
                update_original.updated_at = deleted_at;
                update(&update_original).run(ctx)?;
            }
            Ok(Some(deleted_at))
        } else {
            Ok(None)
        }
    })
}

pub(crate) fn change_owner_node<'a>(
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

const INDEX_TOKEN_LENGTH: usize = 3;

pub(crate) fn full_text_search<'a, Conn: AsReadableConn>(
    query: &'a str,
    filter_by_node_id: Option<&'a Id<Node>>,
    filter_by_posted_in_id: Option<&'a Id<Cotonoma>>,
    only_cotonomas: bool,
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Page<Coto>> + 'a {
    read_op(move |conn| {
        if detect_cjk_chars(query) {
            search_trigram_index(
                conn,
                query,
                filter_by_node_id,
                filter_by_posted_in_id,
                only_cotonomas,
                page_size,
                page_index,
            )
        } else {
            use crate::schema::cotos_fts::dsl::*;
            super::paginate(conn, page_size, page_index, move || {
                // https://sqlite.org/fts5.html#full_text_query_syntax
                let fts_query = query
                    .split_ascii_whitespace()
                    .map(to_fts_phrase)
                    .collect::<Vec<_>>()
                    .join(" AND ");

                let mut query = cotos_fts
                    .select((
                        uuid,
                        rowid,
                        node_id,
                        posted_in_id,
                        posted_by_id,
                        content,
                        summary,
                        media_content,
                        media_type,
                        is_cotonoma,
                        longitude,
                        latitude,
                        datetime_start,
                        datetime_end,
                        repost_of_id,
                        reposted_in_ids,
                        created_at,
                        updated_at,
                    ))
                    .filter(whole_row.eq(fts_query))
                    .into_boxed();
                if let Some(id) = filter_by_node_id {
                    query = query.filter(node_id.eq(id));
                }
                if let Some(id) = filter_by_posted_in_id {
                    query = query.filter(posted_in_id.eq(id));
                }
                if only_cotonomas {
                    query = query.filter(is_cotonoma.eq(true));
                }
                query.order((is_cotonoma.desc(), rank.asc(), created_at.desc()))
            })
        }
    })
}

fn search_trigram_index(
    conn: &mut SqliteConnection,
    query: &str,
    filter_by_node_id: Option<&Id<Node>>,
    filter_by_posted_in_id: Option<&Id<Cotonoma>>,
    only_cotonomas: bool,
    page_size: i64,
    page_index: i64,
) -> Result<Page<Coto>> {
    use crate::schema::cotos_fts_trigram::dsl::*;

    // Convert the space-separated query into a FTS query with `AND` and `OR` operators.
    // https://sqlite.org/fts5.html#full_text_query_syntax

    let mut subqueries: Vec<Cow<'_, str>> = Vec::new();
    for token in query.split_ascii_whitespace() {
        // Tokens that are shorter than trigram terms are turned into a term-search subquery.
        if token.chars().count() < INDEX_TOKEN_LENGTH {
            if let Some(subquery) = make_fts_query_from_short_cjk_token(conn, token)? {
                subqueries.push(Cow::from(format!("({subquery})")))
            } else {
                // No index entries found for the token.
                return Ok(Page::empty_first(page_size));
            }
        } else {
            subqueries.push(Cow::from(token))
        }
    }
    let query = subqueries.join(" AND ");

    super::paginate(conn, page_size, page_index, || {
        let mut query = cotos_fts_trigram
            .select((
                uuid,
                rowid,
                node_id,
                posted_in_id,
                posted_by_id,
                content,
                summary,
                media_content,
                media_type,
                is_cotonoma,
                longitude,
                latitude,
                datetime_start,
                datetime_end,
                repost_of_id,
                reposted_in_ids,
                created_at,
                updated_at,
            ))
            .filter(whole_row.eq(&query))
            .into_boxed();
        if let Some(id) = filter_by_node_id {
            query = query.filter(node_id.eq(id));
        }
        if let Some(id) = filter_by_posted_in_id {
            query = query.filter(posted_in_id.eq(id));
        }
        if only_cotonomas {
            query = query.filter(is_cotonoma.eq(true));
        }
        query.order((is_cotonoma.desc(), rank.asc(), created_at.desc()))
    })
    .with_context(|| format!("Error processing FTS query: [{query}]"))
}

fn make_fts_query_from_short_cjk_token(
    conn: &mut SqliteConnection,
    short_token: &str,
) -> Result<Option<String>> {
    use crate::schema::cotos_fts_trigram_vocab::dsl::*;

    let short_token = escape_like_pattern(short_token, '\\');
    let tokens: Vec<String> = cotos_fts_trigram_vocab
        .filter(term.like(format!("{short_token}%")).escape('\\'))
        .select(term)
        .load::<String>(conn)?;

    if tokens.is_empty() {
        // No index entries found.
        Ok(None)
    } else {
        let query = tokens
            .into_iter()
            .map(|t| to_fts_phrase(&t))
            .collect::<Vec<_>>()
            .join(" OR ");
        Ok(Some(query))
    }
}

fn to_fts_phrase(string: &str) -> String {
    let escaped = string.replace('"', r#""""#);
    format!(r#""{escaped}""#)
}
