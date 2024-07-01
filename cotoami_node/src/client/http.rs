//! HTTP client of Node API Service.
//!
//! This client is stateful and belongs to a single server ([HttpClient::url_prefix()]),
//! so you need to prepare separate clients for each parent that requires plain HTTP access.

use std::sync::Arc;

use anyhow::Result;
use const_format::concatcp;
use futures::future::FutureExt;
use parking_lot::RwLock;
use percent_encoding::{utf8_percent_encode, NON_ALPHANUMERIC};
use reqwest::{
    header,
    header::{HeaderMap, HeaderValue},
    Client, RequestBuilder, StatusCode, Url,
};
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
    url_prefix: Url,
    headers: Arc<RwLock<HeaderMap>>,
}

impl HttpClient {
    pub fn new(url_prefix: &str) -> Result<Self> {
        let client = Client::builder()
            .default_headers(Self::default_headers())
            .build()?;
        Ok(Self {
            client,
            url_prefix: Url::parse(url_prefix)?,
            headers: Arc::new(RwLock::new(HeaderMap::new())),
        })
    }

    pub fn url_prefix(&self) -> &Url { &self.url_prefix }

    fn default_headers() -> HeaderMap {
        let mut headers = HeaderMap::new();
        headers.insert(
            crate::web::CSRF_CUSTOM_HEADER,
            HeaderValue::from_static("cotoami_node"),
        );
        headers
    }

    pub(crate) fn all_headers(&self) -> HeaderMap {
        let mut headers = HeaderMap::new();
        headers.extend(Self::default_headers());
        headers.extend(self.headers.read().clone());
        headers
    }

    fn url(&self, path: &str) -> Url {
        self.url_prefix
            .join(path)
            .unwrap_or_else(|_| unreachable!())
    }

    pub fn get(&self, path: &str) -> RequestBuilder {
        self.client
            .get(self.url(path))
            .headers(self.headers.read().clone())
    }

    pub fn put(&self, path: &str) -> RequestBuilder {
        self.client
            .put(self.url(path))
            .headers(self.headers.read().clone())
    }

    pub fn post(&self, path: &str) -> RequestBuilder {
        self.client
            .post(self.url(path))
            .headers(self.headers.read().clone())
    }

    async fn handle_request(self, request: Request) -> Result<Response> {
        let request_id = *request.id();
        let accept = request.accept();

        // Translate the request's body into an HTTP request (RequestBuilder).
        let http_req = match request.command() {
            Command::LocalNode => self.get(&format!("{API_PATH_NODES}/local")),
            Command::InitialDataset => self.get(API_PATH_DATA),
            Command::ChunkOfChanges { from } => self
                .get(API_PATH_CHANGES)
                .query(&[("from", from.to_string())]),
            Command::CreateClientNodeSession(input) => {
                self.put("/api/session/client-node").json(&input)
            }
            Command::TryConnectServerNode(input) => {
                self.get(&format!("{API_PATH_SERVERS}/try")).query(&input)
            }
            Command::AddServerNode(input) => self.post(API_PATH_SERVERS).form(&input),
            Command::UpdateServerNode { id, values } => {
                self.put(&format!("{API_PATH_SERVERS}/{id}")).form(&values)
            }
            Command::RecentCotonomas { node, pagination } => {
                if let Some(node_id) = node {
                    self.get(&format!("{API_PATH_NODES}/{node_id}/cotonomas"))
                        .query(&pagination)
                } else {
                    self.get(API_PATH_COTONOMAS).query(&pagination)
                }
            }
            Command::Cotonoma { id } => self.get(&format!("{API_PATH_COTONOMAS}/{id}")),
            Command::CotonomaDetails { id } => {
                self.get(&format!("{API_PATH_COTONOMAS}/{id}/details"))
            }
            Command::CotonomaByName { name, node } => {
                let encoded_name = utf8_percent_encode(&name, NON_ALPHANUMERIC).to_string();
                self.get(&format!("{API_PATH_NODES}/{node}/cotonomas/{encoded_name}"))
            }
            Command::SubCotonomas { id, pagination } => self
                .get(&format!("{API_PATH_COTONOMAS}/{id}/subs"))
                .query(&pagination),
            Command::RecentCotos {
                node,
                cotonoma,
                pagination,
            } => {
                if let Some(cotonoma_id) = cotonoma {
                    self.get(&format!("{API_PATH_COTONOMAS}/{cotonoma_id}/cotos"))
                        .query(&pagination)
                } else if let Some(node_id) = node {
                    self.get(&format!("{API_PATH_NODES}/{node_id}/cotos"))
                        .query(&pagination)
                } else {
                    self.get(API_PATH_COTOS).query(&pagination)
                }
            }
            Command::SearchCotos {
                query,
                node,
                cotonoma,
                pagination,
            } => {
                let encoded_query = utf8_percent_encode(&query, NON_ALPHANUMERIC).to_string();
                if let Some(cotonoma_id) = cotonoma {
                    self.get(&format!(
                        "{API_PATH_COTONOMAS}/{cotonoma_id}/cotos/search/{encoded_query}"
                    ))
                    .query(&pagination)
                } else if let Some(node_id) = node {
                    self.get(&format!(
                        "{API_PATH_NODES}/{node_id}/cotos/search/{encoded_query}"
                    ))
                    .query(&pagination)
                } else {
                    self.get(&format!("{API_PATH_COTOS}/search/{encoded_query}"))
                        .query(&pagination)
                }
            }
            Command::GraphFromCoto { coto } => self.get(&format!("{API_PATH_COTOS}/{coto}/graph")),
            Command::GraphFromCotonoma { cotonoma } => {
                self.get(&format!("{API_PATH_COTONOMAS}/{cotonoma}/graph"))
            }
            Command::PostCoto { input, post_to } => self
                .post(&format!("{API_PATH_COTONOMAS}/{post_to}/cotos"))
                .form(&input),
            Command::PostCotonoma { input, post_to } => self
                .post(&format!("{API_PATH_COTONOMAS}/{post_to}/subs"))
                .form(&input),
        };

        // Set the "Accept" header from Request::accept()
        let http_req = match accept {
            SerializeFormat::MessagePack => http_req.header(
                header::ACCEPT,
                HeaderValue::from_static(mime::APPLICATION_MSGPACK.as_ref()),
            ),
            SerializeFormat::Json => http_req.header(
                header::ACCEPT,
                HeaderValue::from_static(mime::APPLICATION_JSON.as_ref()),
            ),
        };

        Self::convert_response(request_id, http_req.send().await?).await
    }

    async fn convert_response(id: Uuid, from: reqwest::Response) -> Result<Response> {
        let body_format = detect_response_body_format(&from);
        if from.status().is_success() {
            return Ok(Response::new(id, body_format, Ok(from.bytes().await?)));
        }

        let error = match from.status() {
            StatusCode::BAD_REQUEST => ServiceError::Request(from.json::<RequestError>().await?),
            StatusCode::UNAUTHORIZED => ServiceError::Unauthorized,
            StatusCode::FORBIDDEN => ServiceError::Permission,
            StatusCode::NOT_FOUND => ServiceError::NotFound(None),
            StatusCode::UNPROCESSABLE_ENTITY => {
                ServiceError::Input(from.json::<InputErrors>().await?)
            }
            StatusCode::INTERNAL_SERVER_ERROR => ServiceError::Server(from.text().await?),
            _ => ServiceError::Unknown("".to_string()),
        };
        Ok(Response::new(id, body_format, Err(error)))
    }
}

const API_PATH_DATA: &'static str = "/api/data";
const API_PATH_CHANGES: &'static str = concatcp!(API_PATH_DATA, "/changes");
const API_PATH_NODES: &'static str = concatcp!(API_PATH_DATA, "/nodes");
const API_PATH_SERVERS: &'static str = concatcp!(API_PATH_NODES, "/servers");
const API_PATH_COTONOMAS: &'static str = concatcp!(API_PATH_DATA, "/cotonomas");
const API_PATH_COTOS: &'static str = concatcp!(API_PATH_DATA, "/cotos");

fn detect_response_body_format(response: &reqwest::Response) -> SerializeFormat {
    // The format will be MessagePack only if the Content-Type header explicitly specifies it,
    // otherwise JSON will be selected as a default.
    if let Some(value) = response.headers().get(header::CONTENT_TYPE) {
        if value == HeaderValue::from_static(mime::APPLICATION_MSGPACK.as_ref()) {
            return SerializeFormat::MessagePack;
        }
    }
    SerializeFormat::Json
}

impl Service<Request> for HttpClient {
    type Response = Response;
    type Error = anyhow::Error;
    type Future = NodeServiceFuture;

    fn call(&self, request: Request) -> Self::Future {
        let this = self.clone();
        async move { this.handle_request(request).await }.boxed()
    }
}

impl NodeService for HttpClient {
    fn description(&self) -> &str { self.url_prefix().as_str() }
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
