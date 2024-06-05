use anyhow::{anyhow, bail, Result};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::{debug, info};

use crate::{
    event::local::LocalNodeEvent,
    service::{
        models::{Changes, ChunkOfChanges},
        service_ext::NodeServiceExt,
        NodeService,
    },
    state::NodeState,
};

impl NodeState {
    pub(crate) async fn sync_with_parent(
        &self,
        parent_node_id: Id<Node>,
        parent_service: Box<dyn NodeService>,
    ) -> Result<Option<(i64, i64)>> {
        let parent_node = self
            .db()
            .globals()
            .parent_node(&parent_node_id)
            .ok_or(anyhow!("ParentNode not found: {parent_node_id}"))?;

        self.pubsub()
            .publish_event(LocalNodeEvent::ParentSyncStart {
                node_id: parent_node_id,
                parent_description: parent_service.description().into(),
            });
        match self.do_sync_with_parent(parent_node, parent_service).await {
            Ok(Some(range)) => {
                self.pubsub().publish_event(LocalNodeEvent::ParentSyncEnd {
                    node_id: parent_node_id,
                    range: Some(range),
                    error: None,
                });
                Ok(Some(range))
            }
            Ok(None) => {
                self.pubsub().publish_event(LocalNodeEvent::ParentSyncEnd {
                    node_id: parent_node_id,
                    range: None,
                    error: None,
                });
                Ok(None)
            }
            Err(e) => {
                self.pubsub().publish_event(LocalNodeEvent::ParentSyncEnd {
                    node_id: parent_node_id,
                    range: None,
                    error: Some(e.to_string()),
                });
                Err(e)
            }
        }
    }

    async fn do_sync_with_parent(
        &self,
        parent_node: ParentNode,
        parent_service: Box<dyn NodeService>,
    ) -> Result<Option<(i64, i64)>> {
        info!(
            "Importing the changes from {}",
            parent_service.description()
        );

        let import_from = parent_node.changes_received + 1;
        let mut from = import_from;
        loop {
            // Get a chunk of changelog entries from the service
            let changes = match parent_service.chunk_of_changes(from).await? {
                ChunkOfChanges::Fetched(changes) => changes,
                ChunkOfChanges::OutOfRange { max } => {
                    if from == import_from && parent_node.changes_received == max {
                        // A case where the local has already synced with the parent
                        info!("Already synced with: {}", parent_service.description());
                        return Ok(None);
                    } else {
                        // The number of `parent_node.changes_received` is larger than
                        // the last number of the changes in the parent node for some reason.
                        // That means the replication has broken between the two nodes.
                        bail!("Tried to import from {from}, but the last change number was {max}.");
                    }
                }
            };
            let is_last_chunk = changes.is_last_chunk();
            let last_number_of_chunk = changes.last_serial_number_of_chunk();
            let last_serial_number = changes.last_serial_number;

            debug!(
                "Fetched a chunk of changes: {}-{} (is_last: {}, max: {})",
                from, last_number_of_chunk, is_last_chunk, last_serial_number
            );

            // Import the changes to the local database
            self.import_changes(parent_node.node_id, changes).await?;

            // Publish progress
            let progress = ((last_number_of_chunk - import_from) as f64
                / (last_serial_number - import_from) as f64)
                * 100f64;
            self.pubsub()
                .publish_event(LocalNodeEvent::ParentSyncProgress {
                    node_id: parent_node.node_id,
                    percent: progress as u8,
                });

            // Next chunk or finish import
            if is_last_chunk {
                info!(
                    "Imported changes {}-{} from {}",
                    import_from,
                    last_number_of_chunk,
                    parent_service.description()
                );
                return Ok(Some((import_from, last_number_of_chunk)));
            } else {
                from = last_number_of_chunk + 1;
            }
        }
    }

    async fn import_changes(&self, parent_node_id: Id<Node>, changes: Changes) -> Result<()> {
        let db = self.db().clone();
        let change_pubsub = self.pubsub().local_changes().clone();
        spawn_blocking(move || {
            let db = db.new_session()?;
            for change in changes.chunk {
                debug!("Importing number {} ...", change.serial_number);
                if let Some(imported_change) = db.import_change(&change, &parent_node_id)? {
                    change_pubsub.publish(imported_change, None);
                }
            }
            Ok(())
        })
        .await?
    }

    pub(crate) async fn handle_parent_change(
        &self,
        parent_node_id: Id<Node>,
        change: ChangelogEntry,
        parent_service: Box<dyn NodeService>,
    ) -> Result<()> {
        info!(
            "Received a change {} from {}",
            change.serial_number,
            parent_service.description()
        );
        let import_result = spawn_blocking({
            let db = self.db().clone();
            move || db.new_session()?.import_change(&change, &parent_node_id)
        })
        .await?;
        match import_result {
            Err(anyhow_err) => {
                if let Some(DatabaseError::UnexpectedChangeNumber {
                    expected, actual, ..
                }) = anyhow_err.downcast_ref::<DatabaseError>()
                {
                    info!(
                        "Out of sync with {} (received: {}, expected {})",
                        parent_service.description(),
                        actual,
                        expected,
                    );
                    self.sync_with_parent(parent_node_id, parent_service)
                        .await?;
                } else {
                    return Err(anyhow_err);
                }
            }
            Ok(Some(imported_change)) => {
                self.pubsub().publish_change(imported_change);
            }
            Ok(None) => (),
        }
        Ok(())
    }
}
