//! A [Service] implementation based on the [crate::pubsub] module.
//!
//! This client sends requests to [PubsubService::requests] and receives
//! responses from [PubsubService::responses], so it is protocol agnostic.

use std::time::Duration;

use anyhow::{bail, Result};
use futures::StreamExt;
use tokio::time::timeout;
use uuid::Uuid;

use crate::{pubsub::Publisher, service::*};

#[derive(Clone)]
pub struct PubsubService {
    description: String,
    requests: RequestPubsub,
    responses: ResponsePubsub,
    timeout: Duration,
}

impl PubsubService {
    const DEFAULT_TIMEOUT: Duration = Duration::from_secs(60);

    pub fn new(description: impl Into<String>, responses: ResponsePubsub) -> Self {
        Self {
            description: description.into(),
            requests: RequestPubsub::default(),
            responses,
            timeout: Self::DEFAULT_TIMEOUT,
        }
    }

    pub fn set_timeout(&mut self, timeout: Duration) { self.timeout = timeout; }

    pub fn requests(&self) -> &RequestPubsub { &self.requests }

    async fn handle_request(self, request: Request) -> Result<Response> {
        let mut stream = self.responses.subscribe_onetime(Some(request.id));
        self.requests.publish(request, None);
        match timeout(self.timeout, stream.next()).await {
            Ok(Some(response)) => Ok(response),
            Ok(None) => bail!("Missing response"),
            Err(_) => bail!("Request timeout"),
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
    fn description(&self) -> Cow<str> { Cow::from(&self.description) }
}
