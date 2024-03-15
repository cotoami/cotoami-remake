//! HTTP client of Node API Service.
//!
//! This client is stateful and belongs to a single server ([HttpClient::url_prefix()]),
//! so you need to prepare separate clients for each parent that requires plain HTTP access.

use std::sync::Arc;

use anyhow::Result;
use futures::future::FutureExt;
use parking_lot::RwLock;
use reqwest::{
    header,
    header::{HeaderMap, HeaderValue},
    Client, RequestBuilder, StatusCode, Url,
};
use uuid::Uuid;

use crate::{
    service::{
        error::{InputErrors, RequestError},
        models::Pagination,
        NodeServiceFuture, *,
    },
    web::PostCoto,
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

    fn url(&self, path: &str, query: Option<Vec<(&str, String)>>) -> Url {
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

    pub fn get(&self, path: &str, query: Option<Vec<(&str, String)>>) -> RequestBuilder {
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
        let accept = request.accept();

        // Translate the request's body into an HTTP request (RequestBuilder).
        let http_req = match request.command() {
            Command::LocalNode => self.get("/api/nodes/local", None),
            Command::ChunkOfChanges { from } => {
                self.get("/api/changes", Some(vec![("from", from.to_string())]))
            }
            Command::CreateClientNodeSession(input) => {
                self.put("/api/session/client-node").json(&input)
            }
            Command::RecentCotos {
                cotonoma,
                pagination,
            } => {
                if let Some(cotonoma_id) = cotonoma {
                    self.get(
                        &format!("/api/cotonomas/{cotonoma_id}/cotos"),
                        Some(pagination.as_query()),
                    )
                } else {
                    self.get("/api/cotos", Some(pagination.as_query()))
                }
            }
            Command::PostCoto {
                content,
                summary,
                post_to,
            } => {
                let form = PostCoto {
                    content: Some(content),
                    summary,
                };
                self.post(&format!("/api/cotonomas/{post_to}/cotos"))
                    .form(&form)
            }
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
            StatusCode::NOT_FOUND => ServiceError::NotFound,
            StatusCode::UNPROCESSABLE_ENTITY => {
                ServiceError::Input(from.json::<InputErrors>().await?)
            }
            StatusCode::INTERNAL_SERVER_ERROR => ServiceError::Server(from.text().await?),
            _ => ServiceError::Unknown("".to_string()),
        };
        Ok(Response::new(id, body_format, Err(error)))
    }
}

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

impl Pagination {
    fn as_query(&self) -> Vec<(&str, String)> {
        let mut query = vec![("page", self.page.to_string())];
        if let Some(page_size) = self.page_size {
            query.push(("page_size", page_size.to_string()));
        }
        query
    }
}
