use anyhow::Result;
use cotoami_db::prelude::*;

use crate::service::{
    models::{ChunkOfChanges, ClientNodeSession, CotoInput, CreateClientNodeSession},
    Command, NodeService, RemoteNodeService,
};

/// An extension trait for [NodeService] that provides shortcut functions for
/// frequently used requests.
pub(crate) trait NodeServiceExt: NodeService {
    async fn chunk_of_changes(&mut self, from: i64) -> Result<ChunkOfChanges> {
        let request = Command::ChunkOfChanges { from }.into_request();
        let response = self.call(request).await?;
        response.content::<ChunkOfChanges>()
    }

    async fn post_coto(&mut self, input: CotoInput, post_to: Id<Cotonoma>) -> Result<Coto> {
        let request = Command::PostCoto { input, post_to }.into_request();
        let response = self.call(request).await?;
        response.content::<Coto>()
    }
}

impl<T> NodeServiceExt for T where T: NodeService + ?Sized {}

pub(crate) trait RemoteNodeServiceExt: RemoteNodeService {
    async fn create_client_node_session(
        &mut self,
        input: CreateClientNodeSession,
    ) -> Result<ClientNodeSession> {
        let request = Command::CreateClientNodeSession(input).into_request();
        let response = self.call(request).await?;
        let client_node_session = response.content::<ClientNodeSession>()?;
        self.set_session_token(&client_node_session.session.token)?;
        Ok(client_node_session)
    }
}

impl<T> RemoteNodeServiceExt for T where T: RemoteNodeService + ?Sized {}
