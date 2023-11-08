//! Client of Node API Service via SSE/

use std::{
    convert::Infallible,
    future::Future,
    pin::Pin,
    task::{Context, Poll},
};

use anyhow::Result;
use tower_service::Service;

use crate::{api::error::*, pubsub::Publisher, service::*};

#[derive(Clone)]
pub struct PubsubClient {
    request_pubsub: Publisher<Request, ()>,
    response_pubsub: Publisher<Response, ()>,
}

impl Service<Request> for PubsubClient {
    type Response = Response;
    type Error = Infallible;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>>>>;

    fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }

    fn call(&mut self, request: Request) -> Self::Future {
        let this = self.clone();
        Box::pin(async move { Ok(Response::new(request.id, Err(ApiError::NotFound))) })
    }
}
