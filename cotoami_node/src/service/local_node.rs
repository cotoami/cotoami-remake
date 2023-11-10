//! Server-side implemention of Node Service,
//! which is intended for non-HTTP protocols such as WebSocket.

use std::{
    future::Future,
    pin::Pin,
    sync::Arc,
    task::{Context, Poll},
};

use bytes::Bytes;
use cotoami_db::prelude::*;
use tower_service::Service;

use crate::{api, api::error::ApiError, service::*, Config, Pubsub};

#[derive(Clone)]
struct LocalNodeService {
    config: Arc<Config>,
    db: Arc<Database>,
    pubsub: Pubsub,
}

impl LocalNodeService {
    async fn handle_request(self, request: Request) -> Result<Bytes, ApiError> {
        match request.body {
            RequestBody::LocalNode => api::nodes::local_node(self.db)
                .await
                .and_then(Self::to_bytes),
            RequestBody::ChunkOfChanges { from } => {
                api::changes::chunk_of_changes(from, self.config.changes_chunk_size, self.db)
                    .await
                    .and_then(Self::to_bytes)
            }
            RequestBody::CreateClientNodeSession(input) => {
                api::session::create_client_node_session(
                    input,
                    self.config.session_seconds(),
                    self.db,
                    self.pubsub.local_change,
                )
                .await
                .and_then(Self::to_bytes)
            }
        }
    }

    fn to_bytes<T: serde::Serialize>(t: T) -> Result<Bytes, ApiError> {
        rmp_serde::to_vec(&t)
            .map(Bytes::from)
            .map_err(ApiError::from)
    }
}

impl Service<Request> for LocalNodeService {
    type Response = Response;
    type Error = anyhow::Error;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>> + Send>>;

    fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }

    fn call(&mut self, request: Request) -> Self::Future {
        let this = self.clone();
        Box::pin(async move {
            Ok(Response::new(
                request.id,
                this.handle_request(request).await,
            ))
        })
    }
}

impl NodeService for LocalNodeService {
    fn description(&self) -> &str { "local-node" }
}
