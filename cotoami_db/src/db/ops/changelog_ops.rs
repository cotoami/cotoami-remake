//! Changelog related operations

use std::ops::DerefMut;

use diesel::prelude::*;

use super::{coto_ops, cotonoma_ops, link_ops, node_ops};
use crate::{
    db::{error::DatabaseError, op::*},
    models::{
        changelog::{Change, ChangelogEntry, NewChangelogEntry},
        node::Node,
        Id,
    },
};

pub fn get<Conn: AsReadableConn>(number: i64) -> impl Operation<Conn, Option<ChangelogEntry>> {
    use crate::schema::changelog::dsl::*;
    read_op(move |conn| {
        changelog
            .find(number)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

pub fn get_last_change_number<Conn: AsReadableConn>(
    node_id: &Id<Node>,
) -> impl Operation<Conn, Option<i64>> + '_ {
    use diesel::dsl::max;

    use crate::schema::changelog::dsl::*;
    read_op(move |conn| {
        changelog
            .select(max(parent_serial_number))
            .filter(parent_node_id.eq(node_id))
            .first(conn)
            .map_err(anyhow::Error::from)
    })
}

pub fn log_change(change: &Change) -> impl Operation<WritableConn, ChangelogEntry> + '_ {
    // insert_new can't return the result directly since the value would
    // reference the local variable `change.new_changelog_entry()`
    composite_op::<WritableConn, _, _>(|ctx| insert_new(&change.new_changelog_entry()).run(ctx))
}

pub fn insert_new<'a>(
    new_entry: &'a NewChangelogEntry<'a>,
) -> impl Operation<WritableConn, ChangelogEntry> + 'a {
    use crate::schema::changelog::dsl::*;
    write_op(move |conn| {
        diesel::insert_into(changelog)
            .values(new_entry)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub fn import_change<'a>(
    parent_node_id: &'a Id<Node>,
    log: &'a ChangelogEntry,
) -> impl Operation<WritableConn, ChangelogEntry> + 'a {
    composite_op::<WritableConn, _, _>(|ctx| {
        // check the serial number of the change
        let last_number = get_last_change_number(parent_node_id).run(ctx)?;
        let expected_number = last_number.map(|n| n + 1).unwrap_or(1);
        if log.serial_number != expected_number {
            Err(DatabaseError::UnexpectedChangeNumber {
                expected: expected_number,
                actual: log.serial_number,
            })?
        }

        // apply the change
        apply_change(&log.change).run(ctx)?;

        // import the remote changelog entry
        insert_new(&log.as_import_from(parent_node_id)).run(ctx)
    })
}

fn apply_change(change: &Change) -> impl Operation<WritableConn, ()> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        match change {
            Change::None => (),
            Change::CreateNode(node, root_cotonoma) => {
                node_ops::import(&node).run(ctx)?;
                if let Some((cotonoma, coto)) = root_cotonoma {
                    coto_ops::insert(&coto.to_import()).run(ctx)?;
                    cotonoma_ops::insert(&cotonoma.to_import()).run(ctx)?;
                }
            }
            Change::ImportNode(node) => {
                node_ops::import(&node).run(ctx)?;
            }
            Change::RenameNode {
                uuid,
                name,
                updated_at,
            } => {
                node_ops::rename(uuid, name, Some(*updated_at)).run(ctx)?;
            }
            Change::CreateCoto(coto) => {
                let new_coto = coto.to_import();
                coto_ops::insert(&new_coto).run(ctx)?;
            }
            Change::EditCoto {
                uuid,
                content,
                summary,
                updated_at,
            } => {
                let coto = coto_ops::get(uuid).run(ctx)?.unwrap();
                let mut update_coto = coto.to_update();
                update_coto.content = content.as_deref();
                update_coto.summary = summary.as_deref();
                update_coto.updated_at = *updated_at;
                coto_ops::update(&update_coto).run(ctx)?;
            }
            Change::DeleteCoto(id) => {
                coto_ops::delete(id).run(ctx)?;
            }
            Change::CreateCotonoma(cotonoma, coto) => {
                coto_ops::insert(&coto.to_import()).run(ctx)?;
                cotonoma_ops::insert(&cotonoma.to_import()).run(ctx)?;
            }
            Change::RenameCotonoma {
                uuid,
                name,
                updated_at,
            } => {
                cotonoma_ops::rename(uuid, name, Some(*updated_at)).run(ctx)?;
            }
            Change::DeleteCotonoma(id) => {
                cotonoma_ops::delete(id).run(ctx)?;
            }
            Change::CreateLink(link) => {
                let new_link = link.to_import();
                link_ops::insert(&new_link).run(ctx)?;
            }
            Change::EditLink {
                uuid,
                linking_phrase,
                updated_at,
            } => {
                link_ops::update_linking_phrase(uuid, linking_phrase.as_deref(), Some(*updated_at))
                    .run(ctx)?;
            }
            Change::DeleteLink(id) => {
                link_ops::delete(id).run(ctx)?;
            }
        }
        Ok(())
    })
}
