use anyhow::Result;
use cotoami_db::prelude::*;

use crate::service::{
    models::{ChunkOfChanges, ClientNodeSession, CreateClientNodeSession},
    NodeService, RemoteNodeService, RequestBody,
};

pub trait NodeServiceExt: NodeService {
    async fn chunk_of_changes(&mut self, from: i64) -> Result<ChunkOfChanges> {
        let request = RequestBody::ChunkOfChanges { from }.into_request();
        let response = self.call(request).await?;
        response.message_pack::<ChunkOfChanges>()
    }

    async fn post_coto(
        &mut self,
        content: String,
        summary: Option<String>,
        post_to: Id<Cotonoma>,
    ) -> Result<Coto> {
        let request = RequestBody::PostCoto {
            content,
            summary,
            post_to,
        }
        .into_request();
        let response = self.call(request).await?;
        response.message_pack::<Coto>()
    }
}

impl<T> NodeServiceExt for T where T: NodeService + ?Sized {}

pub trait RemoteNodeServiceExt: RemoteNodeService {
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

impl<T> RemoteNodeServiceExt for T where T: RemoteNodeService + ?Sized {}
