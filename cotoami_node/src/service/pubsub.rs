//! A [Service] implementation based on the [crate::pubsub] module.
//!
//! This client sends requests to [PubsubService::requests] and receives
//! responses from [PubsubService::responses], so it is protocol agnostic.

use anyhow::{bail, Result};
use futures::StreamExt;
use uuid::Uuid;

use crate::{pubsub::Publisher, service::*};

#[derive(Clone)]
pub struct PubsubService {
    description: String,
    requests: RequestPubsub,
    responses: ResponsePubsub,
}

impl PubsubService {
    pub fn new(description: impl Into<String>, responses: ResponsePubsub) -> Self {
        Self {
            description: description.into(),
            requests: RequestPubsub::default(),
            responses,
        }
    }

    pub fn requests(&self) -> &RequestPubsub { &self.requests }

    async fn handle_request(self, request: Request) -> Result<Response> {
        let mut stream = self.responses.subscribe_onetime(Some(request.id));
        self.requests.publish(request, None);
        // TODO: should be set timeout for the response
        // https://github.com/hyperium/hyper/issues/2132
        // https://docs.rs/tower/latest/tower/struct.ServiceBuilder.html
        //     - check timeout and map_err
        // https://docs.rs/tower/latest/tower/timeout/error/struct.Elapsed.html
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
    type Future = NodeServiceFuture;

    fn call(&self, request: Request) -> Self::Future {
        let this = self.clone();
        Box::pin(async move { this.handle_request(request).await })
    }
}

impl NodeService for PubsubService {
    fn description(&self) -> &str { &self.description }
}
