//! Cotonoma related operations

use super::coto_ops;
use crate::db::op::*;
use crate::models::coto::{Coto, Cotonoma, NewCoto, NewCotonoma};
use crate::models::node::Node;
use crate::models::Id;
use diesel::prelude::*;
use std::ops::DerefMut;

pub fn get(cotonoma_id: &Id<Cotonoma>) -> impl ReadOperation<Option<(Cotonoma, Coto)>> + '_ {
    use crate::schema::{cotonomas, cotos};
    read_op(move |conn| {
        cotonomas::table
            .inner_join(cotos::table)
            .filter(cotonomas::uuid.eq(cotonoma_id))
            .select((Cotonoma::as_select(), Coto::as_select()))
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn create_root<'a>(
    node_id: &'a Id<Node>,
    name: &'a str,
) -> impl Operation<WritableConnection, (Cotonoma, Coto)> + 'a {
    composite_op::<WritableConnection, _, _>(move |ctx| {
        let new_coto = NewCoto::new_root_cotonoma(node_id, name)?;
        let inserted_coto = coto_ops::insert_new(&new_coto).run(ctx)?;
        let new_cotonoma = NewCotonoma::new(node_id, &inserted_coto.uuid, name)?;
        let inserted_cotonoma = insert_new(&new_cotonoma).run(ctx)?;
        Ok((inserted_cotonoma, inserted_coto))
    })
}

pub fn create<'a>(
    node_id: &'a Id<Node>,
    posted_in_id: &'a Id<Cotonoma>,
    posted_by_id: &'a Id<Node>,
    name: &'a str,
) -> impl Operation<WritableConnection, (Cotonoma, Coto)> + 'a {
    composite_op::<WritableConnection, _, _>(move |ctx| {
        let new_coto = NewCoto::new_cotonoma(node_id, posted_in_id, posted_by_id, name)?;
        let inserted_coto = coto_ops::insert_new(&new_coto).run(ctx)?;
        let new_cotonoma = NewCotonoma::new(node_id, &inserted_coto.uuid, name)?;
        let inserted_cotonoma = insert_new(&new_cotonoma).run(ctx)?;
        Ok((inserted_cotonoma, inserted_coto))
    })
}

pub fn insert_new<'a>(
    new_cotonoma: &'a NewCotonoma<'a>,
) -> impl Operation<WritableConnection, Cotonoma> + 'a {
    use crate::schema::cotonomas::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(cotonomas)
            .values(new_cotonoma)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}
