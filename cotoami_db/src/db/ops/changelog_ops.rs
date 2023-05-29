//! Changelog related operations

use crate::db::op::*;
use crate::models::changelog::{Change, ChangelogEntry, NewChangelogEntry};
use crate::models::node::Node;
use crate::models::Id;
use diesel::prelude::*;
use std::ops::DerefMut;

pub fn get(number: i64) -> impl ReadOperation<Option<ChangelogEntry>> {
    use crate::schema::changelog::dsl::*;
    read_op(move |conn| {
        changelog
            .find(number)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn get_last_change_number(node_id: Id<Node>) -> impl ReadOperation<Option<i64>> {
    use crate::schema::changelog::dsl::*;
    use diesel::dsl::max;
    read_op(move |conn| {
        changelog
            .select(max(parent_serial_number))
            .filter(parent_node_id.eq(node_id))
            .first(conn)
            .map_err(anyhow::Error::from)
    })
}

pub fn log_change<'a>(
    change: &'a Change,
) -> impl Operation<WritableConnection, ChangelogEntry> + 'a {
    // insert_new can't return the result directly since the value would
    // reference the local variable `change.new_changelog_entry()`
    composite_op::<WritableConnection, _, _>(move |ctx| {
        insert_new(&change.new_changelog_entry()).run(ctx)
    })
}

pub fn insert_new<'a>(
    new_entry: &'a NewChangelogEntry<'a>,
) -> impl Operation<WritableConnection, ChangelogEntry> + 'a {
    use crate::schema::changelog::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(changelog)
            .values(new_entry)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}
