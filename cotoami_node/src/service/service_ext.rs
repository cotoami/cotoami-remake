use std::sync::Arc;

use anyhow::{bail, Result};
use async_trait::async_trait;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::{debug, info};

use crate::{
    api,
    api::session::{ClientNodeSession, CreateClientNodeSession},
    service::{NodeService, RemoteNodeService, RequestBody},
    state::{ChangePubsub, ChunkOfChanges},
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
