//! Coto related operations

use std::{collections::HashMap, ops::DerefMut};

use anyhow::{Context, Result};
use diesel::prelude::*;
use validator::Validate;

use super::{cotonoma_ops, Paginated};
use crate::{
    db::{error::*, op::*, ops::detect_cjk_chars},
    models::{
        coto::{Coto, NewCoto, UpdateCoto},
        cotonoma::Cotonoma,
        node::Node,
        Id,
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

pub(crate) fn contains<Conn: AsReadableConn>(id: &Id<Coto>) -> impl Operation<Conn, bool> + '_ {
    read_op(move |conn| {
        let count: i64 = cotos::table
            .select(diesel::dsl::count_star())
            .filter(cotos::uuid.eq(id))
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

pub(crate) fn get_by_ids<Conn: AsReadableConn>(
    ids: Vec<Id<Coto>>,
) -> impl Operation<Conn, Vec<Coto>> {
    read_op(move |conn| {
        let mut map: HashMap<Id<Coto>, Coto> = cotos::table
            .filter(cotos::uuid.eq_any(&ids))
            .load::<Coto>(conn)?
            .into_iter()
            .map(|c| (c.uuid, c))
            .collect();
        // Sort the results in order of the `ids` param.
        let cotonomas = ids.iter().map(|id| map.remove(id)).flatten().collect();
        Ok(cotonomas)
    })
}

pub(crate) fn recent<'a, Conn: AsReadableConn>(
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

pub(crate) fn insert<'a>(new_coto: &'a NewCoto<'a>) -> impl Operation<WritableConn, Coto> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let coto: Coto = diesel::insert_into(cotos::table)
            .values(new_coto)
            .get_result(ctx.conn().deref_mut())?;

        // Increment the number of posts in the cotonoma
        if let Some(posted_in_id) = coto.posted_in_id.as_ref() {
            cotonoma_ops::update_number_of_posts(posted_in_id, 1).run(ctx)?;
        }

        Ok(coto)
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

pub(crate) fn delete(id: &Id<Coto>) -> impl Operation<WritableConn, bool> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        // The links connected to this coto will be also deleted by FOREIGN KEY ON DELETE CASCADE.
        // If it is a cotonoma, the corresponding cotonoma row will be also deleted by
        // FOREIGN KEY ON DELETE CASCADE.
        let deleted: Option<Coto> = diesel::delete(cotos::table.find(id))
            .get_result(ctx.conn().deref_mut())
            .optional()?;

        if let Some(coto) = deleted {
            // Decrement the number of posts in the cotonoma
            if let Some(posted_in_id) = coto.posted_in_id.as_ref() {
                cotonoma_ops::update_number_of_posts(posted_in_id, -1).run(ctx)?;
            }
            Ok(true)
        } else {
            Ok(false)
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

pub(crate) fn update_number_of_outgoing_links(
    id: &Id<Coto>,
    delta: i32,
) -> impl Operation<WritableConn, i32> + '_ {
    write_op(move |conn| {
        let coto: Coto = diesel::update(cotos::table.find(id))
            .set(cotos::outgoing_links.eq(cotos::outgoing_links + delta))
            .get_result(conn.deref_mut())?;
        Ok(coto.outgoing_links)
    })
}

const INDEX_TOKEN_LENGTH: usize = 3;

// https://sqlite.org/fts5.html#full_text_query_syntax
pub(crate) fn full_text_search<'a, Conn: AsReadableConn>(
    query: &'a str,
    filter_by_node_id: Option<&'a Id<Node>>,
    filter_by_posted_in_id: Option<&'a Id<Cotonoma>>,
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Paginated<Coto>> + 'a {
    read_op(move |conn| {
        if detect_cjk_chars(query) {
            search_trigram_index(
                conn,
                query,
                filter_by_node_id,
                filter_by_posted_in_id,
                page_size,
                page_index,
            )
        } else {
            use crate::schema::cotos_fts::dsl::*;
            super::paginate(conn, page_size, page_index, || {
                let mut query = cotos_fts
                    .select((
                        uuid,
                        rowid,
                        node_id,
                        posted_in_id,
                        posted_by_id,
                        content,
                        summary,
                        is_cotonoma,
                        repost_of_id,
                        reposted_in_ids,
                        created_at,
                        updated_at,
                        outgoing_links,
                    ))
                    .filter(whole_row.eq(to_fts_phrase(query)))
                    .into_boxed();
                if let Some(id) = filter_by_node_id {
                    query = query.filter(node_id.eq(id));
                }
                if let Some(id) = filter_by_posted_in_id {
                    query = query.filter(posted_in_id.eq(id));
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
    page_size: i64,
    page_index: i64,
) -> Result<Paginated<Coto>> {
    use crate::schema::{cotos_fts_trigram::dsl::*, cotos_fts_trigram_vocab::dsl::*};

    let query = if query.chars().count() < INDEX_TOKEN_LENGTH {
        let tokens: Vec<String> = cotos_fts_trigram_vocab
            .filter(term.like(format!("{query}%")))
            .select(term)
            .load::<String>(conn)?;
        if tokens.is_empty() {
            // No index entries found.
            return Ok(Paginated::empty_first(page_size));
        } else {
            tokens
                .into_iter()
                .map(|t| to_fts_phrase(&t))
                .collect::<Vec<_>>()
                .join(" OR ")
        }
    } else {
        to_fts_phrase(query)
    };
    // println!("query: [{query}]");

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
                is_cotonoma,
                repost_of_id,
                reposted_in_ids,
                created_at,
                updated_at,
                outgoing_links,
            ))
            .filter(whole_row.eq(&query))
            .into_boxed();
        if let Some(id) = filter_by_node_id {
            query = query.filter(node_id.eq(id));
        }
        if let Some(id) = filter_by_posted_in_id {
            query = query.filter(posted_in_id.eq(id));
        }
        query.order((is_cotonoma.desc(), rank.asc(), created_at.desc()))
    })
    .with_context(|| format!("Error processing FTS query: [{query}]"))
}

fn to_fts_phrase(string: &str) -> String {
    let escaped = string.replace(r#"""#, r#""""#);
    format!(r#""{escaped}""#)
}
