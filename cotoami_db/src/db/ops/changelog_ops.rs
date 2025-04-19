//! Changelog related operations

use std::ops::DerefMut;

use anyhow::{bail, ensure};
use diesel::{dsl::max, prelude::*};
use tracing::debug;

use super::{coto_ops, cotonoma_ops, graph_ops, ito_ops, node_ops, node_role_ops::parent_ops};
use crate::{
    db::{error::*, op::*},
    models::{
        changelog::{Change, ChangelogEntry, NewChangelogEntry},
        node::{local::LocalNode, parent::ParentNode, Node},
        Id,
    },
    schema::changelog,
};

pub(crate) fn last_serial_number<Conn: AsReadableConn>() -> impl Operation<Conn, Option<i64>> {
    read_op(move |conn| {
        changelog::table
            .select(max(changelog::serial_number))
            .first(conn)
            .map_err(anyhow::Error::from)
    })
}

fn last_origin_serial_number<Conn: AsReadableConn>(
    node_id: &Id<Node>,
) -> impl Operation<Conn, Option<i64>> + '_ {
    read_op(move |conn| {
        changelog::table
            .select(max(changelog::origin_serial_number))
            .filter(changelog::origin_node_id.eq(node_id))
            .first(conn)
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn chunk<Conn: AsReadableConn>(
    from: i64,
    limit: i64,
) -> impl Operation<Conn, (Vec<ChangelogEntry>, i64)> {
    composite_op::<Conn, _, _>(move |ctx| {
        let last = last_serial_number().run(ctx)?.unwrap_or(0);
        if from >= 1 && from <= last {
            Ok((
                changelog::table
                    .filter(changelog::serial_number.ge(from))
                    .order(changelog::serial_number.asc())
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

pub(crate) fn log_change<'a>(
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

pub(crate) fn insert<'a>(
    new_entry: &'a NewChangelogEntry<'a>,
) -> impl Operation<WritableConn, ChangelogEntry> + 'a {
    write_op(move |conn| {
        diesel::insert_into(changelog::table)
            .values(new_entry)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

pub(crate) fn contains_change<Conn: AsReadableConn>(
    log: &ChangelogEntry,
) -> impl Operation<Conn, bool> + '_ {
    read_op(move |conn| {
        changelog::table
            .count()
            .filter(
                changelog::origin_node_id
                    .eq(log.origin_node_id)
                    .and(changelog::origin_serial_number.eq(log.origin_serial_number)),
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
pub(crate) fn import_change<'a>(
    log: &'a ChangelogEntry,
    parent_node: &'a mut ParentNode,
    local_node: &'a LocalNode,
) -> impl Operation<WritableConn, Option<ChangelogEntry>> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        // Check if the local node has been forked from the parent
        ensure!(
            !parent_node.forked,
            DatabaseError::AlreadyForkedFromParent {
                parent_node_id: parent_node.node_id
            }
        );

        // Check the serial number of the change
        let expected_number = parent_node.changes_received + 1;
        ensure!(
            log.serial_number == expected_number,
            DatabaseError::UnexpectedChangeNumber {
                expected: expected_number,
                actual: log.serial_number,
                parent_node_id: parent_node.node_id,
            }
        );

        // Import the change only if the same change has not yet been imported before.
        let imported_log = if contains_change(log).run(ctx)? {
            debug!(
                "Skipping change {} from parent:{} (origin: {}, number: {})",
                log.serial_number,
                parent_node.node_id,
                log.origin_node_id,
                log.origin_serial_number
            );
            None
        } else {
            let apply_result = apply_change(&log.change, local_node).run(ctx);

            // Record the applied change log.
            let mut log_to_import = log.to_import();
            if let Err(e) = apply_result {
                // https://docs.rs/anyhow/latest/anyhow/struct.Error.html#display-representations
                log_to_import.set_import_error(format!("{e:#?}"));
            }
            Some(insert(&log_to_import).run(ctx)?)
        };

        // Increment the count of received changes
        *parent_node = parent_ops::increment_changes_received(
            &parent_node.node_id,
            expected_number,
            // If the same change has already been received from another parent,
            // `log_entry` should be None, so the current timpstamp will be used
            // as the `received_at` timpstamp.
            imported_log.as_ref().map(|e| e.inserted_at),
        )
        .run(ctx)?;

        Ok(imported_log)
    })
}

fn apply_change<'a>(
    change: &'a Change,
    local_node: &'a LocalNode,
) -> impl Operation<WritableConn, ()> + 'a {
    let image_max_size = local_node.image_max_size();
    composite_op::<WritableConn, _, _>(move |ctx| {
        match change {
            Change::None => (),
            Change::CreateNode { node, root } => {
                node_ops::upsert(node).run(ctx)?;
                if let Some((cotonoma, coto)) = root {
                    coto_ops::insert(&coto.to_import(image_max_size)?).run(ctx)?;
                    cotonoma_ops::insert(&cotonoma.to_import()).run(ctx)?;
                }
            }
            Change::UpsertNode(node) => {
                node_ops::upsert(node).run(ctx)?;
            }
            Change::RenameNode {
                node_id,
                name,
                updated_at,
            } => {
                node_ops::rename(node_id, name, Some(*updated_at)).run(ctx)?;
            }
            Change::SetNodeIcon { node_id, icon } => {
                node_ops::set_icon(node_id, icon.as_ref()).run(ctx)?;
            }
            Change::SetRootCotonoma {
                node_id,
                cotonoma_id,
            } => {
                node_ops::set_root_cotonoma(node_id, cotonoma_id).run(ctx)?;
            }
            Change::CreateCoto(coto) => {
                coto_ops::insert(&coto.to_import(image_max_size)?).run(ctx)?;
            }
            Change::EditCoto {
                coto_id,
                diff,
                updated_at,
            } => {
                // Accept the image size from a parent by skipping resizing (image_max_size as None).
                coto_ops::edit(coto_id, diff, None, Some(*updated_at)).run(ctx)?;
            }
            Change::Promote {
                coto_id,
                promoted_at,
            } => {
                coto_ops::promote(coto_id, Some(*promoted_at)).run(ctx)?;
            }
            Change::DeleteCoto {
                coto_id,
                deleted_at,
            } => {
                coto_ops::delete(coto_id, Some(*deleted_at)).run(ctx)?;
            }
            Change::CreateCotonoma(cotonoma, coto) => {
                coto_ops::insert(&coto.to_import(image_max_size)?).run(ctx)?;
                cotonoma_ops::insert(&cotonoma.to_import()).run(ctx)?;
            }
            Change::RenameCotonoma {
                cotonoma_id,
                name,
                updated_at,
            } => {
                cotonoma_ops::rename(cotonoma_id, name, Some(*updated_at)).run(ctx)?;
            }
            Change::CreateIto(ito) => {
                ito_ops::insert(ito.to_import()).run(ctx)?;
            }
            Change::EditIto {
                ito_id,
                diff,
                updated_at,
            } => {
                ito_ops::edit(ito_id, diff, Some(*updated_at)).run(ctx)?;
            }
            Change::DeleteIto { ito_id } => {
                ito_ops::delete(ito_id).run(ctx)?;
            }
            Change::ChangeItoOrder { ito_id, new_order } => {
                ito_ops::change_order(ito_id, *new_order).run(ctx)?;
            }
            Change::ChangeOwnerNode {
                from,
                to,
                last_change_number,
            } => {
                let last_change_number_in_local = last_origin_serial_number(from)
                    .run(ctx)?
                    .unwrap_or_else(|| unreachable!());
                if last_change_number_in_local == *last_change_number {
                    graph_ops::change_owner_node(from, to).run(ctx)?;
                } else {
                    bail!("Couldn't change the owner node due to version mismatching (expected: {}, actual: {}).", 
                        last_change_number, last_change_number_in_local);
                }
            }
        }
        Ok(())
    })
}
