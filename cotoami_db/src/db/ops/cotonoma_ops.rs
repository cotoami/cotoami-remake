//! Cotonoma related operations

use crate::db::op::{read_op, ReadOperation};
use crate::models::coto::{Coto, Cotonoma};
use crate::models::Id;
use diesel::prelude::*;

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
