//! ChildNode related operations

use std::ops::DerefMut;

use diesel::prelude::*;

use crate::{
    db::op::*,
    models::node::child::{ChildNode, NewChildNode},
};

pub fn insert<'a>(
    new_child_node: &'a NewChildNode<'a>,
) -> impl Operation<WritableConn, ChildNode> + 'a {
    use crate::schema::child_nodes::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(child_nodes)
            .values(new_child_node)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn get_by_session_token<'a>(
    token: &'a str,
) -> impl Operation<WritableConn, Option<ChildNode>> + 'a {
    use crate::schema::child_nodes::dsl::*;
    read_op(move |conn| {
        child_nodes
            .filter(session_token.eq(token))
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}
