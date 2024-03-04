//! Web API for Node operations based on [NodeState].

use std::{net::SocketAddr, sync::Arc};

use anyhow::Result;
use axum::{
    extract::OriginalUri,
    headers,
    http::{
        header::{self, HeaderName, HeaderValue},
        Request, StatusCode, Uri,
    },
    middleware,
    middleware::Next,
    response::{IntoResponse, Response},
    routing::get,
    Extension, Json, Router,
};
use axum_extra::extract::cookie::{Cookie, CookieJar};
use bytes::Bytes;
use cotoami_db::prelude::ClientSession;
use dotenvy::dotenv;
use futures::TryFutureExt;
use mime::Mime;
use tokio::{
    sync::{oneshot, oneshot::Sender},
    task::{spawn_blocking, JoinHandle},
};
use tracing::{debug, error};
use validator::Validate;

use crate::{service::ServiceError, state::NodeState};

mod changes;
mod cotonomas;
mod cotos;
mod csrf;
mod events;
mod nodes;
mod session;
mod ws;

pub(crate) use self::{cotonomas::PostCoto, csrf::CUSTOM_HEADER as CSRF_CUSTOM_HEADER};

pub async fn launch_server(
    server_config: ServerConfig,
    node_state: NodeState,
) -> Result<(JoinHandle<Result<()>>, Sender<()>)> {
    // Build a Web API server
    let addr = SocketAddr::from(([0, 0, 0, 0], server_config.port));
    let web_api = router(Arc::new(server_config), node_state);
    let server = axum::Server::bind(&addr).serve(web_api.into_make_service());

    // Prepare a way to gracefully shutdown a server
    // https://hyper.rs/guides/0.14/server/graceful-shutdown/
    let (tx, rx) = oneshot::channel::<()>();
    let server = server.with_graceful_shutdown(async {
        rx.await.ok();
    });

    // Launch the server
    Ok((tokio::spawn(server.map_err(anyhow::Error::from)), tx))
}

#[derive(Debug, serde::Deserialize, Validate)]
pub struct ServerConfig {
    // COTOAMI_PORT
    #[serde(default = "ServerConfig::default_port")]
    pub port: u16,

    // COTOAMI_URL_SCHEME
    #[serde(default = "ServerConfig::default_url_scheme")]
    pub url_scheme: String,
    // COTOAMI_URL_HOST
    #[serde(default = "ServerConfig::default_url_host")]
    pub url_host: String,
    // COTOAMI_URL_PORT
    pub url_port: Option<u16>,
}

impl ServerConfig {
    const ENV_PREFXI: &'static str = "COTOAMI_SERVER_";

    pub fn load_from_env() -> Result<ServerConfig, envy::Error> {
        dotenv().ok();
        envy::prefixed(Self::ENV_PREFXI).from_env::<ServerConfig>()
    }

    // Functions returning a default value as a workaround for the issue:
    // https://github.com/serde-rs/serde/issues/368
    fn default_port() -> u16 { 5103 }
    fn default_url_scheme() -> String { "http".into() }
    fn default_url_host() -> String { "localhost".into() }
}

/////////////////////////////////////////////////////////////////////////////
// Router
/////////////////////////////////////////////////////////////////////////////

pub(super) fn router(config: Arc<ServerConfig>, state: NodeState) -> Router {
    Router::new()
        .nest("/api", routes())
        .fallback(fallback)
        .layer(middleware::from_fn(csrf::protect_from_forgery))
        // NOTE: the axum doc recommends to use [tower::ServiceBuilder] to apply multiple
        // middleware at once, but as far as I tested, middlewares can't see the `Extension`
        // set by a preceding middleware in the same `ServiceBuilder` (it causes "Missing request
        // extension" error).
        // https://docs.rs/axum/latest/axum/middleware/index.html#applying-multiple-middleware
        .layer(Extension(config))
        .layer(Extension(state.clone()))
        .with_state(state)
}

fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(|| async { "Cotoami Node API" }))
        .nest("/ws", ws::routes())
        .nest("/session", session::routes())
        .nest("/events", events::routes())
        .nest("/changes", changes::routes())
        .nest("/nodes", nodes::routes())
        .nest("/cotos", cotos::routes())
        .nest("/cotonomas", cotonomas::routes())
}

async fn fallback(uri: Uri) -> impl IntoResponse {
    (StatusCode::NOT_FOUND, format!("No route: {}", uri.path()))
}

/////////////////////////////////////////////////////////////////////////////
// Response Content
/////////////////////////////////////////////////////////////////////////////
struct Content<T>(T, Accept);

impl<T> IntoResponse for Content<T>
where
    T: serde::Serialize,
{
    fn into_response(self) -> Response {
        let Content(content, accept) = self;
        if accept.contains(mime::APPLICATION_MSGPACK) {
            match rmp_serde::to_vec(&content).map(Bytes::from) {
                Ok(bytes) => (
                    [(
                        header::CONTENT_TYPE,
                        HeaderValue::from_static(mime::APPLICATION_MSGPACK.as_ref()),
                    )],
                    bytes,
                )
                    .into_response(),
                Err(err) => (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    [(
                        header::CONTENT_TYPE,
                        HeaderValue::from_static(mime::TEXT_PLAIN_UTF_8.as_ref()),
                    )],
                    err.to_string(),
                )
                    .into_response(),
            }
        } else {
            Json(content).into_response()
        }
    }
}

/// Accept [headers::Header] implementation to be used with an [axum::TypedHeader] extractor.
/// It's a just-enough implementation (not involving any parsing) for [Content] to work.
#[derive(Debug, Clone)]
struct Accept {
    value: String,
}

impl Accept {
    fn contains(&self, mime: Mime) -> bool { self.value.contains(mime.as_ref()) }
}

impl headers::Header for Accept {
    fn name() -> &'static HeaderName { &axum::http::header::ACCEPT }

    fn decode<'i, I>(values: &mut I) -> Result<Self, headers::Error>
    where
        Self: Sized,
        I: Iterator<Item = &'i HeaderValue>,
    {
        // `values` contains multiple values if there are multiple headers with the same key `accept`
        // https://docs.rs/http/0.2.9/http/header/struct.HeaderMap.html#method.get_all
        // so, it needs only the first value in this case.
        let value = values.next().ok_or_else(headers::Error::invalid)?;

        // The value may contain opaque bytes
        let value_str = value.to_str().map_err(|_| headers::Error::invalid())?;

        Ok(Self {
            value: value_str.to_owned(),
        })
    }

    fn encode<E: Extend<HeaderValue>>(&self, values: &mut E) {
        let header_value = HeaderValue::from_str(&self.value)
            .expect("The value contains only visible ASCII characters.");
        values.extend(::std::iter::once(header_value));
    }
}

/////////////////////////////////////////////////////////////////////////////
// Error
/////////////////////////////////////////////////////////////////////////////

// Tell axum how to convert `ServiceError` into a response.
impl IntoResponse for ServiceError {
    fn into_response(self) -> Response {
        match self {
            ServiceError::Request(e) => (StatusCode::BAD_REQUEST, Json(e)).into_response(),
            ServiceError::Unauthorized => StatusCode::UNAUTHORIZED.into_response(),
            ServiceError::Permission => StatusCode::FORBIDDEN.into_response(),
            ServiceError::NotFound => StatusCode::NOT_FOUND.into_response(),
            ServiceError::Input(e) => (StatusCode::UNPROCESSABLE_ENTITY, Json(e)).into_response(),
            ServiceError::NotImplemented => StatusCode::NOT_IMPLEMENTED.into_response(),
            ServiceError::Server(e) => {
                let message = format!("Server error: {e}");
                error!(message);
                (StatusCode::INTERNAL_SERVER_ERROR, message).into_response()
            }
            ServiceError::Unknown(_) => unreachable!(),
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Session
/////////////////////////////////////////////////////////////////////////////

const SESSION_COOKIE_NAME: &str = "session_token";

// https://github.com/rust-lang/rust-clippy/issues/9776
#[allow(clippy::declare_interior_mutable_const)]
pub(crate) const SESSION_HEADER_NAME: HeaderName =
    HeaderName::from_static("x-cotoami-session-token");

/// A middleware function to load the session by a token stored in a cookie or header value
async fn require_session<B>(
    Extension(state): Extension<NodeState>,
    // CookieJar extractor will never reject a request
    // https://docs.rs/axum-extra/0.7.5/src/axum_extra/extract/cookie/mod.rs.html#96
    // https://docs.rs/axum/latest/axum/extract/index.html#optional-extractors
    jar: CookieJar,
    mut request: Request<B>,
    next: Next<B>,
) -> Result<Response, ServiceError> {
    let cookie_value = jar.get(SESSION_COOKIE_NAME).map(Cookie::value);
    let header_value = request
        .headers()
        .get(SESSION_HEADER_NAME)
        .and_then(|v| v.to_str().ok());

    let token = if let Some(token) = cookie_value.or(header_value) {
        token.to_string() // create an owned string to be used in spawn_blocking
    } else {
        return Err(ServiceError::Unauthorized); // missing session token
    };

    let session = spawn_blocking(move || {
        let mut db = state.db().new_session()?;
        // https://rust-lang.github.io/async-book/07_workarounds/02_err_in_async_blocks.html
        Ok::<_, ServiceError>(db.client_session(&token)?)
    })
    .await??;

    if let Some(session) = session {
        debug!(
            "Client session [{} {}]: {session:?}",
            request.method(),
            request.extensions().get::<OriginalUri>().unwrap().0
        );
        request.extensions_mut().insert(session);
        Ok(next.run(request).await)
    } else {
        Err(ServiceError::Unauthorized) // invalid token (session expired, etc.)
    }
}

/////////////////////////////////////////////////////////////////////////////
// Operator
/////////////////////////////////////////////////////////////////////////////

/// A middleware function to identify the operator from a session.
///
/// This middleware has to be placed after the [require_session] middleware.
async fn require_operator<B>(
    Extension(session): Extension<ClientSession>,
    mut request: Request<B>,
    next: Next<B>,
) -> Result<Response, ServiceError> {
    if let ClientSession::Operator(operator) = session {
        request.extensions_mut().insert(operator);
        Ok(next.run(request).await)
    } else {
        Err(ServiceError::Permission)
    }
}
