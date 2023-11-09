//! Node API Client via non-HTTP protocols such as WebSocket.
//!
//! This client sends requests to [PubsubClient::request_pubsub] and receives
//! responses from [PubsubClient::response_pubsub], so it is protocol agnostic.

use std::{
    future::Future,
    pin::Pin,
    task::{Context, Poll},
};

use anyhow::{bail, Result};
use futures::StreamExt;
use tower_service::Service;
use uuid::Uuid;

use crate::{pubsub::Publisher, service::*};

#[derive(Clone)]
pub struct PubsubClient {
    pub request_pubsub: RequestPubsub,
    pub response_pubsub: ResponsePubsub,
}

impl PubsubClient {
    pub fn new() -> Self {
        Self {
            request_pubsub: RequestPubsub::new(),
            response_pubsub: ResponsePubsub::new(),
        }
    }

    async fn handle_request(self, request: Request) -> Result<Response> {
        let mut stream = self.response_pubsub.subscribe_onetime(Some(request.id));
        self.request_pubsub.publish(request, None);
        if let Some(response) = stream.next().await {
            Ok(response)
        } else {
            bail!("Missing response.");
        }
    }
}

type RequestPubsub = Publisher<Request, ()>;
type ResponsePubsub = Publisher<Response, Uuid>;

impl Service<Request> for PubsubClient {
    type Response = Response;
    type Error = anyhow::Error;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>>>>;

    fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }

    fn call(&mut self, request: Request) -> Self::Future {
        let this = self.clone();
        Box::pin(async move { this.handle_request(request).await })
    }
}
