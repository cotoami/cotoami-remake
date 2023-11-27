//! A [Service] implementation based on the [crate::pubsub] module.
//!
//! This client sends requests to [PubsubService::requests] and receives
//! responses from [PubsubService::responses], so it is protocol agnostic.

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
pub struct PubsubService {
    requests: RequestPubsub,
    responses: ResponsePubsub,
}

impl PubsubService {
    pub fn new(responses: ResponsePubsub) -> Self {
        Self {
            requests: RequestPubsub::new(),
            responses,
        }
    }

    pub fn requests(&self) -> &RequestPubsub { &self.requests }

    async fn handle_request(self, request: Request) -> Result<Response> {
        let mut stream = self.responses.subscribe_onetime(Some(request.id));
        self.requests.publish(request, None);
        if let Some(response) = stream.next().await {
            Ok(response)
        } else {
            bail!("Missing response.");
        }
    }
}

pub type RequestPubsub = Publisher<Request, ()>;
pub type ResponsePubsub = Publisher<Response, Uuid>;

impl Service<Request> for PubsubService {
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
