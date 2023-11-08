//! Server-side implemention of Node API Service.

use std::{
    convert::Infallible,
    future::Future,
    pin::Pin,
    sync::Arc,
    task::{Context, Poll},
};

use bytes::Bytes;
use cotoami_db::prelude::*;
use tower_service::Service;

use super::*;
use crate::{api, api::error::ApiError, Config};

#[derive(Clone)]
struct NodeApi {
    config: Arc<Config>,
    db: Arc<Database>,
}

impl NodeApi {
    async fn handle_request(self, request: &Request) -> Result<Bytes, ApiError> {
        match request.body {
            RequestBody::LocalNode => api::nodes::local_node(self.db)
                .await
                .and_then(Self::to_bytes),
            RequestBody::ChunkOfChanges { from } => {
                api::changes::chunk_of_changes(from, self.config.changes_chunk_size, self.db)
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

impl Service<Request> for NodeApi {
    type Response = Response;
    type Error = Infallible;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>>>>;

    fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }

    fn call(&mut self, request: Request) -> Self::Future {
        let this = self.clone();
        Box::pin(async move {
            Ok(Response::new(
                request.id,
                this.handle_request(&request).await,
            ))
        })
    }
}
