//! Cotonoma related operations

use super::coto_ops;
use super::Paginated;
use crate::db::op::*;
use crate::models::coto::{Coto, Cotonoma, NewCoto, NewCotonoma, UpdateCotonoma};
use crate::models::node::Node;
use crate::models::Id;
use chrono::NaiveDateTime;
use diesel::prelude::*;
use std::ops::DerefMut;
use validator::Validate;

pub fn get<Conn: AsReadableConn>(
    id: &Id<Cotonoma>,
) -> impl Operation<Conn, Option<(Cotonoma, Coto)>> + '_ {
    use crate::schema::{cotonomas, cotos};
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

pub fn recent<Conn: AsReadableConn>(
    node_id: Option<&Id<Node>>,
    page_size: i64,
    page_index: i64,
) -> impl Operation<Conn, Paginated<Cotonoma>> + '_ {
    use crate::schema::cotonomas;
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

pub fn create_root<'a>(
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

pub fn create<'a>(
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

pub fn insert<'a>(
    new_cotonoma: &'a NewCotonoma<'a>,
) -> impl Operation<WritableConn, Cotonoma> + 'a {
    use crate::schema::cotonomas::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(cotonomas)
            .values(new_cotonoma)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn update<'a>(
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

pub fn delete(id: &Id<Cotonoma>) -> impl Operation<WritableConn, bool> + '_ {
    use crate::schema::cotonomas::dsl::*;
    write_op(move |conn| {
        let affected = diesel::delete(cotonomas.find(id)).execute(conn.deref_mut())?;
        Ok(affected > 0)
    })
}

pub fn rename<'a>(
    id: &'a Id<Cotonoma>,
    name: &'a str,
    updated_at: Option<NaiveDateTime>,
) -> impl Operation<WritableConn, Option<(Cotonoma, Coto)>> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let updated_at = updated_at.unwrap_or(crate::current_datetime());
        if let Some((cotonoma, coto)) = get(id).run(ctx)? {
            // Update coto
            let mut coto = coto.to_update();
            coto.summary = Some(name);
            coto.updated_at = updated_at;
            let updated_coto = coto_ops::update(&coto).run(ctx)?;

            // Update cotonoma
            let mut cotonoma = cotonoma.to_update();
            cotonoma.name = name;
            cotonoma.updated_at = updated_at;
            let updated_cotonoma = update(&cotonoma).run(ctx)?;

            Ok(Some((updated_cotonoma, updated_coto)))
        } else {
            Ok(None)
        }
    })
}
