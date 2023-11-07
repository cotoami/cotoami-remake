//! HTTP Client of Node API Service.

use std::{
    future::Future,
    pin::Pin,
    sync::Arc,
    task::{Context, Poll},
};

use anyhow::Result;
use parking_lot::RwLock;
use reqwest::{
    header::{HeaderMap, HeaderValue},
    Client, Url,
};
use tower_service::Service;

use crate::{csrf, http, service::*};

#[derive(Clone)]
pub struct HttpClient {
    client: Client,
    url_prefix: String,
    headers: Arc<RwLock<HeaderMap>>,
}

impl HttpClient {
    pub fn new(url_prefix: String) -> Result<Self> {
        let client = Client::builder()
            .default_headers(Self::default_headers())
            .build()?;
        Ok(Self {
            client,
            url_prefix,
            headers: Arc::new(RwLock::new(HeaderMap::new())),
        })
    }

    pub fn url_prefix(&self) -> &str { &self.url_prefix }

    pub fn set_session_token(&mut self, token: &str) -> Result<()> {
        let mut token = HeaderValue::from_str(token)?;
        token.set_sensitive(true);
        self.headers
            .write()
            .insert(http::SESSION_HEADER_NAME, token);
        Ok(())
    }

    fn default_headers() -> HeaderMap {
        let mut headers = HeaderMap::new();
        headers.insert(
            csrf::CUSTOM_HEADER,
            HeaderValue::from_static("cotoami_node"),
        );
        headers
    }

    fn url(&self, path: &str, query: Option<Vec<(&str, &str)>>) -> Url {
        let mut url = Url::parse(&self.url_prefix).unwrap_or_else(|_| unreachable!());
        url = url.join(path).unwrap_or_else(|_| unreachable!());
        if let Some(query) = query {
            let mut pairs = url.query_pairs_mut();
            for (name, value) in query.iter() {
                pairs.append_pair(name, value);
            }
        }
        url
    }

    async fn get(
        &self,
        path: &str,
        query: Option<Vec<(&str, &str)>>,
    ) -> Result<reqwest::Response, reqwest::Error> {
        let url = self.url(path, query);
        self.client
            .get(url)
            .headers(self.headers.read().clone())
            .send()
            .await
    }

    async fn handle_request(self, request: &Request) -> Result<Response, reqwest::Error> {
        match request.body {
            RequestBody::GetLocalNode => {
                let body = self.get("/api/nodes/local", None).await?.bytes().await?;
                Ok(Response::new(request.id, Ok(body)))
            }
        }
    }
}

impl Service<Request> for HttpClient {
    type Response = Response;
    type Error = reqwest::Error;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>>>>;

    fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }

    fn call(&mut self, request: Request) -> Self::Future {
        let this = self.clone();
        Box::pin(async move { this.handle_request(&request).await })
    }
}
