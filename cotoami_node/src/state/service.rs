//! Server-side implemention of Node Service,
//! which is intended for non-HTTP protocols such as WebSocket.

use std::{
    future::Future,
    pin::Pin,
    task::{Context, Poll},
};

use anyhow::Result;
use bytes::Bytes;
use tower_service::Service;

use crate::{service::*, state::AppState};

impl AppState {
    async fn handle_request(self, request: Request) -> Result<Bytes> {
        match request.body() {
            RequestBody::LocalNode => self.local_node().await.and_then(Self::to_bytes),
            RequestBody::ChunkOfChanges { from } => {
                self.chunk_of_changes(from).await.and_then(Self::to_bytes)
            }
            RequestBody::CreateClientNodeSession(input) => self
                .create_client_node_session(input)
                .await
                .and_then(Self::to_bytes),
        }
    }

    fn to_bytes<T: serde::Serialize>(t: T) -> Result<Bytes> {
        rmp_serde::to_vec(&t)
            .map(Bytes::from)
            .map_err(anyhow::Error::from)
    }
}

impl Service<Request> for AppState {
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
                *request.id(),
                this.handle_request(request)
                    .await
                    .map_err(ServiceError::from),
            ))
        })
    }
}

impl NodeService for AppState {
    fn description(&self) -> &str { "local-node" }
}
