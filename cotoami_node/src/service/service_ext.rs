use anyhow::Result;
use async_trait::async_trait;

use crate::{
    api::session::{ClientNodeSession, CreateClientNodeSession},
    service::{NodeService, RemoteNodeService, RequestBody},
    state::ChunkOfChanges,
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
