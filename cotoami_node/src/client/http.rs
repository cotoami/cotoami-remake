//! HTTP client of Node API Service.
//!
//! This client is stateful and belongs to a single server ([HttpClient::url_prefix()]),
//! so you need to prepare separate clients for each parent that requires plain HTTP access.

use std::{
    sync::Arc,
    task::{Context, Poll},
};

use anyhow::Result;
use futures::future::FutureExt;
use parking_lot::RwLock;
use reqwest::{
    header,
    header::{HeaderMap, HeaderValue},
    Client, RequestBuilder, StatusCode, Url,
};
use tower_service::Service;
use uuid::Uuid;

use crate::service::{
    error::{InputErrors, RequestError},
    NodeServiceFuture, *,
};

/// [HttpClient] provides the featuers of the [RemoteNodeService] trait by
/// connecting to a Node Web API server via HTTP/HTTPS.
///
/// You do **not** have to wrap the `HttpClient` in an [`Arc`] to **reuse** it,
/// because it already uses an [`Arc`] internally.
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

    fn default_headers() -> HeaderMap {
        let mut headers = HeaderMap::new();
        headers.insert(
            header::ACCEPT,
            HeaderValue::from_static(mime::APPLICATION_MSGPACK.as_ref()),
        );
        headers.insert(
            crate::web::CSRF_CUSTOM_HEADER,
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

    pub fn get(&self, path: &str, query: Option<Vec<(&str, &str)>>) -> RequestBuilder {
        let url = self.url(path, query);
        self.client.get(url).headers(self.headers.read().clone())
    }

    pub fn put(&self, path: &str) -> RequestBuilder {
        let url = self.url(path, None);
        self.client.put(url).headers(self.headers.read().clone())
    }

    pub fn post(&self, path: &str) -> RequestBuilder {
        let url = self.url(path, None);
        self.client.post(url).headers(self.headers.read().clone())
    }

    async fn handle_request(self, request: Request) -> Result<Response> {
        let request_id = *request.id();
        let http_req = match request.body() {
            RequestBody::LocalNode => self.get("/api/nodes/local", None),
            RequestBody::ChunkOfChanges { from } => {
                self.get("/api/changes", Some(vec![("from", &from.to_string())]))
            }
            RequestBody::CreateClientNodeSession(input) => {
                self.put("/api/session/client-node").json(&input)
            }
        };
        Self::convert_response(request_id, http_req.send().await?).await
    }

    async fn convert_response(id: Uuid, from: reqwest::Response) -> Result<Response> {
        if from.status().is_success() {
            return Ok(Response::new(id, Ok(from.bytes().await?)));
        }

        let error = match from.status() {
            StatusCode::BAD_REQUEST => ServiceError::Request(from.json::<RequestError>().await?),
            StatusCode::UNAUTHORIZED => ServiceError::Unauthorized,
            StatusCode::FORBIDDEN => ServiceError::Permission,
            StatusCode::NOT_FOUND => ServiceError::NotFound,
            StatusCode::UNPROCESSABLE_ENTITY => {
                ServiceError::Input(from.json::<InputErrors>().await?)
            }
            StatusCode::INTERNAL_SERVER_ERROR => ServiceError::Server(from.text().await?),
            _ => ServiceError::Unknown("".to_string()),
        };
        Ok(Response::new(id, Err(error)))
    }
}

impl Service<Request> for HttpClient {
    type Response = Response;
    type Error = anyhow::Error;
    type Future = NodeServiceFuture;

    fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }

    fn call(&mut self, request: Request) -> Self::Future {
        let this = self.clone();
        async move { this.handle_request(request).await }.boxed()
    }
}

impl NodeService for HttpClient {
    fn description(&self) -> &str { self.url_prefix() }
}

impl RemoteNodeService for HttpClient {
    fn set_session_token(&mut self, token: &str) -> Result<()> {
        let mut token = HeaderValue::from_str(token)?;
        token.set_sensitive(true);
        self.headers
            .write()
            .insert(crate::web::SESSION_HEADER_NAME, token);
        Ok(())
    }
}
