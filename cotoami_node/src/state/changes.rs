use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::debug;

use crate::state::AppState;

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
}
