use anyhow::{bail, Result};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::{debug, info};

use crate::{
    service::{service_ext::NodeServiceExt, NodeService},
    state::AppState,
};

#[derive(serde::Serialize, serde::Deserialize)]
pub enum ChunkOfChanges {
    Fetched(Changes),
    OutOfRange { max: i64 },
}

#[derive(serde::Serialize, serde::Deserialize)]
pub struct Changes {
    pub chunk: Vec<ChangelogEntry>,
    pub last_serial_number: i64,
}

impl Changes {
    pub fn last_serial_number_of_chunk(&self) -> i64 {
        self.chunk.last().map(|c| c.serial_number).unwrap_or(0)
    }

    pub fn is_last_chunk(&self) -> bool {
        if let Some(change) = self.chunk.last() {
            // For safety's sake (to avoid infinite loop), leave it as the last chunk
            // if the last serial number of it is equal **or larger than** the
            // last serial number of all, rather than exactly the same number.
            change.serial_number >= self.last_serial_number
        } else {
            true // empty (no changes) means the last chunk
        }
    }
}

impl AppState {
    pub async fn chunk_of_changes(&self, from: i64, chunk_size: i64) -> Result<ChunkOfChanges> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let mut db = db.new_session()?;
            match db.chunk_of_changes(from, chunk_size) {
                Ok((chunk, last_serial_number)) => Ok(ChunkOfChanges::Fetched(Changes {
                    chunk,
                    last_serial_number,
                })),
                Err(anyhow_err) => {
                    if let Some(DatabaseError::ChangeNumberOutOfRange { max, .. }) =
                        anyhow_err.downcast_ref::<DatabaseError>()
                    {
                        Ok(ChunkOfChanges::OutOfRange { max: *max })
                    } else {
                        Err(anyhow_err.into())
                    }
                }
            }
        })
        .await?
    }

    pub async fn import_changes(&self, parent_node_id: Id<Node>, changes: Changes) -> Result<()> {
        let db = self.db().clone();
        let change_pubsub = self.pubsub().local_change.clone();
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

    async fn sync_with_parent<S>(
        &mut self,
        parent_node_id: Id<Node>,
        parent_service: &mut S,
    ) -> Result<Option<(i64, i64)>>
    where
        S: NodeService + Send,
        S::Future: Send,
    {
        info!(
            "Importing the changes from {}",
            parent_service.description()
        );
        let parent_node = {
            let db = self.db().new_session()?;
            db.parent_node_or_err(&parent_node_id, &db.local_node_as_operator()?)?
        };

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
                        bail!(
                            "Tried to import from {}, but the last change number was {}.",
                            from,
                            max
                        );
                    }
                }
            };
            let is_last_chunk = changes.is_last_chunk();
            let last_number_of_chunk = changes.last_serial_number_of_chunk();

            debug!(
                "Fetched a chunk of changes: {}-{} (is_last: {}, max: {})",
                from, last_number_of_chunk, is_last_chunk, changes.last_serial_number
            );

            // Import the changes to the local database
            self.import_changes(parent_node_id, changes).await?;

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
}
