//! Server-side implemention of Node API Service.

use std::{
    convert::Infallible,
    future::Future,
    pin::Pin,
    sync::Arc,
    task::{Context, Poll},
};

use cotoami_db::prelude::*;
use tower_service::Service;

use super::*;
use crate::api::{error::ApiError, nodes};

#[derive(Clone)]
struct NodeApi {
    db: Arc<Database>,
}

impl NodeApi {
    async fn handle_request(self, request: &Request) -> Result<Vec<u8>, ApiError> {
        match request.body {
            RequestBody::GetLocalNode => nodes::get_local_node(self.db)
                .await
                .and_then(Self::to_binary),
        }
    }

    fn to_binary<T: serde::Serialize>(t: T) -> Result<Vec<u8>, ApiError> {
        rmp_serde::to_vec(&t).map_err(ApiError::from)
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
            Ok(Response {
                id: request.id,
                body: this.handle_request(&request).await,
            })
        })
    }
}
