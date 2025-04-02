//! Web API for Node operations based on [NodeState].

use std::{future::IntoFuture, net::SocketAddr, sync::Arc, time::Duration};

use anyhow::Result;
use axum::{
    extract::{OriginalUri, Request},
    http::{
        header::{self, HeaderName, HeaderValue},
        StatusCode, Uri,
    },
    middleware,
    middleware::Next,
    response::{IntoResponse, Response},
    routing::get,
    Extension, Json, Router,
};
use axum_extra::{
    extract::cookie::{Cookie, CookieJar},
    headers,
};
use bytes::Bytes;
use cotoami_db::prelude::ClientSession;
use futures::TryFutureExt;
use mime::Mime;
use tokio::{
    net::TcpListener,
    sync::{oneshot, oneshot::Sender},
    task::{spawn_blocking, JoinHandle},
};
use tower_http::{timeout::TimeoutLayer, trace::TraceLayer};
use tracing::{debug, error};

use crate::{config::ServerConfig, service::ServiceError, state::NodeState};

mod csrf;
mod data;
mod events;
mod session;
mod ws;

pub(crate) use self::csrf::CUSTOM_HEADER as CSRF_CUSTOM_HEADER;

pub async fn launch_server(
    config: ServerConfig,
    node_state: NodeState,
) -> Result<(JoinHandle<Result<()>>, Sender<()>)> {
    let config = Arc::new(config);

    // Create an API application
    let api = router(config.clone(), node_state.clone())
        .into_make_service_with_connect_info::<SocketAddr>();

    // Run the server with graceful shutdown
    let addr = SocketAddr::from(([0, 0, 0, 0], config.port));
    let listener = TcpListener::bind(addr).await.unwrap();
    let (shutdown_trigger, rx) = oneshot::channel::<()>();
    let serve = axum::serve(listener, api)
        .with_graceful_shutdown({
            let node_state = node_state.clone();
            async move {
                rx.await.ok();
                node_state.shutdown().await;
            }
        })
        .into_future()
        .map_err(anyhow::Error::from);

    // Put the server config to the state
    node_state.set_local_server_config(config);

    Ok((tokio::spawn(serve), shutdown_trigger))
}

/////////////////////////////////////////////////////////////////////////////
// Router
/////////////////////////////////////////////////////////////////////////////

pub(super) fn router(config: Arc<ServerConfig>, node_state: NodeState) -> Router {
    Router::new()
        .nest("/api", routes(config.enable_websocket))
        .fallback(fallback)
        .layer(middleware::from_fn(csrf::protect_from_forgery))
        // NOTE: the axum doc recommends to use [tower::ServiceBuilder] to apply multiple
        // middleware at once, but as far as I tested, middlewares can't see the `Extension`
        // set by a preceding middleware in the same `ServiceBuilder` (it causes "Missing request
        // extension" error).
        // https://docs.rs/axum/latest/axum/middleware/index.html#applying-multiple-middleware
        .layer(Extension(config))
        .layer(Extension(node_state.clone()))
        .layer(TraceLayer::new_for_http())
        .layer(TimeoutLayer::new(Duration::from_secs(10)))
        .with_state(node_state)
}

fn routes(enable_websocket: bool) -> Router<NodeState> {
    let routes = Router::new()
        .route("/", get(|| async { "Cotoami Node API" }))
        .nest("/session", session::routes())
        .nest("/events", events::routes())
        .nest("/data", data::routes());

    if enable_websocket {
        routes.nest("/ws", ws::routes())
    } else {
        routes
    }
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
            ServiceError::NotFound(..) => StatusCode::NOT_FOUND.into_response(),
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
async fn require_session(
    Extension(state): Extension<NodeState>,
    // CookieJar extractor will never reject a request
    // https://docs.rs/axum-extra/0.7.5/src/axum_extra/extract/cookie/mod.rs.html#96
    // https://docs.rs/axum/latest/axum/extract/index.html#optional-extractors
    jar: CookieJar,
    mut request: Request,
    next: Next,
) -> Result<Response, ServiceError> {
    let cookie_value = jar.get(SESSION_COOKIE_NAME).map(Cookie::value);
    let header_value = request
        .headers()
        .get(SESSION_HEADER_NAME)
        .and_then(|v| v.to_str().ok());

    let token = if let Some(token) = cookie_value.or(header_value) {
        token.to_string() // create an owned string to be used in spawn_blocking
    } else {
        if state
            .db()
            .globals()
            .try_read_local_node()?
            .anonymous_read_enabled
        {
            "".into() // dummy token as an anonymous client
        } else {
            return Err(ServiceError::Unauthorized); // missing session token
        }
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

// https://github.com/rust-lang/rust-clippy/issues/9776
#[allow(clippy::declare_interior_mutable_const)]
pub(crate) const OPERATE_AS_OWNER_HEADER_NAME: HeaderName =
    HeaderName::from_static("x-cotoami-operate-as-owner");

/// A middleware function to identify the operator from a session.
///
/// This middleware has to be placed after the [require_session] middleware.
async fn require_operator(
    Extension(state): Extension<NodeState>,
    Extension(session): Extension<ClientSession>,
    mut request: Request,
    next: Next,
) -> Result<Response, ServiceError> {
    if let ClientSession::Operator(operator) = session {
        let operator = if request.headers().contains_key(OPERATE_AS_OWNER_HEADER_NAME) {
            if operator.has_owner_permission() {
                state.db().globals().local_node_as_operator()?
            } else {
                return Err(ServiceError::Permission);
            }
        } else {
            operator
        };
        request.extensions_mut().insert(operator);
        Ok(next.run(request).await)
    } else {
        Err(ServiceError::Permission)
    }
}
