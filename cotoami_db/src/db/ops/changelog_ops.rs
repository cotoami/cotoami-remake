//! Changelog related operations

use std::ops::DerefMut;

use anyhow::ensure;
use diesel::prelude::*;
use tracing::debug;

use super::{coto_ops, cotonoma_ops, link_ops, node_ops, parent_node_ops};
use crate::{
    db::{error::*, op::*},
    models::{
        changelog::{Change, ChangelogEntry, NewChangelogEntry},
        node::{parent::ParentNode, Node},
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

pub fn last_serial_number<Conn: AsReadableConn>() -> impl Operation<Conn, Option<i64>> {
    use diesel::dsl::max;

    use crate::schema::changelog::dsl::*;
    read_op(move |conn| {
        changelog
            .select(max(serial_number))
            .first(conn)
            .map_err(anyhow::Error::from)
    })
}

pub fn last_origin_serial_number<Conn: AsReadableConn>(
    node_id: &Id<Node>,
) -> impl Operation<Conn, Option<i64>> + '_ {
    use diesel::dsl::max;

    use crate::schema::changelog::dsl::*;
    read_op(move |conn| {
        changelog
            .select(max(origin_serial_number))
            .filter(origin_node_id.eq(node_id))
            .first(conn)
            .map_err(anyhow::Error::from)
    })
}

pub fn chunk<Conn: AsReadableConn>(
    from: i64,
    limit: i64,
) -> impl Operation<Conn, (Vec<ChangelogEntry>, i64)> {
    use crate::schema::changelog::dsl::*;
    composite_op::<Conn, _, _>(move |ctx| {
        let last = last_serial_number().run(ctx)?.unwrap_or(0);
        if from >= 1 && from <= last {
            Ok((
                changelog
                    .filter(serial_number.ge(from))
                    .order(serial_number.asc())
                    .limit(limit)
                    .load::<ChangelogEntry>(ctx.conn().readable())
                    .map_err(anyhow::Error::from)?,
                last,
            ))
        } else {
            Err(DatabaseError::ChangeNumberOutOfRange {
                number: from,
                max: last,
            })?
        }
    })
}

pub fn log_change<'a>(
    change: &'a Change,
    local_node_id: &'a Id<Node>,
) -> impl Operation<WritableConn, ChangelogEntry> + 'a {
    composite_op::<WritableConn, _, _>(|ctx| {
        let last_number = last_origin_serial_number(local_node_id)
            .run(ctx)?
            .unwrap_or(0);
        let new_entry = change.new_changelog_entry(local_node_id, last_number + 1);
        insert(&new_entry).run(ctx)
    })
}

pub fn insert<'a>(
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

pub fn contains_change<Conn: AsReadableConn>(
    log: &ChangelogEntry,
) -> impl Operation<Conn, bool> + '_ {
    use crate::schema::changelog::dsl::*;
    read_op(move |conn| {
        changelog
            .count()
            .filter(
                origin_node_id
                    .eq(log.origin_node_id)
                    .and(origin_serial_number.eq(log.origin_serial_number)),
            )
            .get_result(conn)
            .map(|c: i64| c > 0)
            .map_err(anyhow::Error::from)
    })
}

/// Imports a change sent from the `parent_node`.
///
/// ## Ensure to apply changes in order of the serial number for each `parent_node`
///
/// The serial number of the change has to be the value of
/// [ParentNode::changes_received] plus one, otherwise an error will be returned.
/// After the change has been accepted successfully, the value of
/// [ParentNode::changes_received] will be incremented and saved in the database.
///
/// ## The possibility of the same changes from multiple parent nodes
///
/// The same changes (with the same serial number in the same origin) could be sent
/// from multiple parent nodes. If the database already contains the same change,
/// the change will be ignored yet the `changes_received` will be incremented.
pub fn import_change<'a>(
    log: &'a ChangelogEntry,
    parent_node: &'a mut ParentNode,
) -> impl Operation<WritableConn, Option<ChangelogEntry>> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        // Check the serial number of the change
        let expected_number = parent_node.changes_received + 1;
        ensure!(
            log.serial_number == expected_number,
            DatabaseError::UnexpectedChangeNumber {
                expected: expected_number,
                actual: log.serial_number,
                parent_node_id: parent_node.node_id.into(),
            }
        );

        // Import the change only if the same change has not yet been imported before.
        let log_entry = if contains_change(&log).run(ctx)? {
            debug!(
                "Change {} skipped (origin node: {}, origin number: {})",
                log.serial_number, log.origin_node_id, log.origin_serial_number
            );
            None
        } else {
            apply_change(&log.change).run(ctx)?;
            let log_entry = insert(&log.to_import()).run(ctx)?;
            Some(log_entry)
        };

        // Increment the count of received changes
        *parent_node = parent_node_ops::increment_changes_received(
            &parent_node.node_id,
            expected_number,
            log_entry.as_ref().map(|e| e.inserted_at),
        )
        .run(ctx)?;

        Ok(log_entry)
    })
}

fn apply_change(change: &Change) -> impl Operation<WritableConn, ()> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        match change {
            Change::None => (),
            Change::CreateNode(node, root_cotonoma) => {
                node_ops::upsert(node).run(ctx)?;
                if let Some((cotonoma, coto)) = root_cotonoma {
                    coto_ops::insert(&coto.to_import()).run(ctx)?;
                    cotonoma_ops::insert(&cotonoma.to_import()).run(ctx)?;
                }
            }
            Change::UpsertNode(node) => {
                node_ops::upsert(node).run(ctx)?;
            }
            Change::RenameNode {
                uuid,
                name,
                updated_at,
            } => {
                node_ops::rename(uuid, name, Some(*updated_at)).run(ctx)?;
            }
            Change::SetRootCotonoma { uuid, cotonoma_id } => {
                node_ops::set_root_cotonoma(uuid, cotonoma_id).run(ctx)?;
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
                let mut update_coto = coto.edit(content, summary.as_deref());
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
