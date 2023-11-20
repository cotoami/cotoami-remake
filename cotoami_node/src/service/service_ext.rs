use std::sync::Arc;

use anyhow::{bail, Result};
use async_trait::async_trait;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::{debug, info};

use crate::{
    api,
    api::{
        changes::ChunkOfChanges,
        session::{ClientNodeSession, CreateClientNodeSession},
    },
    service::{NodeService, RemoteNodeService, RequestBody},
    ChangePubsub,
};

#[async_trait]
pub trait NodeServiceExt: NodeService
where
    Self::Future: Send,
{
    async fn chunk_of_changes(&mut self, from: i64) -> Result<ChunkOfChanges> {
        let request = RequestBody::ChunkOfChanges { from }.into_request();
        let response = self.call(request).await?;
        response.message_pack::<ChunkOfChanges>()
    }

    async fn import_changes(
        &mut self,
        parent_node_id: Id<Node>,
        db: &Arc<Database>,
        change_pubsub: &Arc<ChangePubsub>,
    ) -> Result<Option<(i64, i64)>> {
        info!("Importing the changes from {}", self.description());
        let parent_node = {
            let db = db.new_session()?;
            db.parent_node_or_err(&parent_node_id, &db.local_node_as_operator()?)?
        };

        let import_from = parent_node.changes_received + 1;
        let mut from = import_from;
        loop {
            // Get a chunk of changelog entries from the service
            let changes = match self.chunk_of_changes(from).await? {
                ChunkOfChanges::Fetched(changes) => changes,
                ChunkOfChanges::OutOfRange { max } => {
                    if from == import_from && parent_node.changes_received == max {
                        // A case where the local has already synced with the parent
                        info!("Already synced with: {}", self.description());
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
            api::changes::import_changes(
                parent_node_id,
                changes,
                db.clone(),
                change_pubsub.clone(),
            )
            .await?;

            // Next chunk or finish import
            if is_last_chunk {
                info!(
                    "Imported changes {}-{} from {}",
                    import_from,
                    last_number_of_chunk,
                    self.description()
                );
                return Ok(Some((import_from, last_number_of_chunk)));
            } else {
                from = last_number_of_chunk + 1;
            }
        }
    }

    async fn handle_parent_change(
        &mut self,
        parent_node_id: Id<Node>,
        change: ChangelogEntry,
        db: Arc<Database>,
        change_pubsub: &Arc<ChangePubsub>,
    ) -> Result<()> {
        let import_result = spawn_blocking({
            let db = db.clone();
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
                        self.description(),
                        actual,
                        expected,
                    );
                    self.import_changes(parent_node_id, &db, change_pubsub)
                        .await?;
                } else {
                    return Err(anyhow_err);
                }
            }
            Ok(Some(imported_change)) => {
                change_pubsub.publish(imported_change, None);
            }
            Ok(None) => (),
        }
        Ok(())
    }
}

impl<T> NodeServiceExt for T
where
    T: NodeService,
    Self::Future: Send,
{
}

#[async_trait]
pub trait RemoteNodeServiceExt: RemoteNodeService
where
    Self::Future: Send,
{
    async fn create_client_node_session(
        &mut self,
        input: CreateClientNodeSession,
    ) -> Result<ClientNodeSession> {
        let request = RequestBody::CreateClientNodeSession(input).into_request();
        let response = self.call(request).await?;
        let client_node_session = response.message_pack::<ClientNodeSession>()?;
        self.set_session_token(&client_node_session.session.token)?;
        Ok(client_node_session)
    }
}

impl<T> RemoteNodeServiceExt for T
where
    T: RemoteNodeService,
    Self::Future: Send,
{
}
