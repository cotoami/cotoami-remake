use axum::{
    http::{header::HeaderName, Request, StatusCode, Uri},
    middleware,
    middleware::Next,
    response::{IntoResponse, Response},
    routing::{get, put},
    Extension, Json, Router,
};
use axum_extra::extract::cookie::{Cookie, CookieJar};
use tokio::task::spawn_blocking;
use tracing::{debug, error};

use crate::{service::ServiceError, state::NodeState};

mod changes;
pub(crate) mod clients;
mod cotonomas;
mod cotos;
pub(crate) mod csrf;
mod events;
pub(crate) mod router;
pub(crate) mod servers;
mod session;

/////////////////////////////////////////////////////////////////////////////
// Router
/////////////////////////////////////////////////////////////////////////////

pub(super) fn router(state: NodeState) -> Router {
    Router::new()
        .nest("/api", routes())
        .fallback(fallback)
        .layer(middleware::from_fn(csrf::protect_from_forgery))
        .layer(Extension(state.clone())) // for middleware
        .with_state(state)
}

fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(|| async { "Cotoami Node API" }))
        .nest("/session", session::routes())
        .nest("/events", events::routes())
        .nest("/changes", changes::routes())
        .nest(
            "/nodes",
            Router::new()
                .route("/local", get(self::router::local_node))
                .nest(
                    "/servers",
                    Router::new()
                        .route(
                            "/",
                            get(self::servers::all_servers).post(self::servers::add_server_node),
                        )
                        .route("/:node_id", put(self::servers::update_server_node))
                        .layer(middleware::from_fn(require_session)),
                )
                .nest(
                    "/clients",
                    Router::new()
                        .route(
                            "/",
                            get(self::clients::recent_client_nodes)
                                .post(self::clients::add_client_node),
                        )
                        .layer(middleware::from_fn(require_session)),
                )
                .nest(
                    "parents",
                    Router::new()
                        .route("/:node_id/fork", put(self::router::fork_from_parent))
                        .layer(middleware::from_fn(require_session)),
                )
                .layer(middleware::from_fn(require_session)),
        )
        .nest("/cotos", cotos::routes())
        .nest("/cotonomas", cotonomas::routes())
}

async fn fallback(uri: Uri) -> impl IntoResponse {
    (StatusCode::NOT_FOUND, format!("No route: {}", uri.path()))
}

/////////////////////////////////////////////////////////////////////////////
// Error
/////////////////////////////////////////////////////////////////////////////

// Tell axum how to convert `ApiError` into a response.
impl IntoResponse for ServiceError {
    fn into_response(self) -> Response {
        match self {
            ServiceError::Request(e) => (StatusCode::BAD_REQUEST, Json(e)).into_response(),
            ServiceError::Unauthorized => StatusCode::UNAUTHORIZED.into_response(),
            ServiceError::Permission => StatusCode::FORBIDDEN.into_response(),
            ServiceError::NotFound => StatusCode::NOT_FOUND.into_response(),
            ServiceError::Input(e) => (StatusCode::UNPROCESSABLE_ENTITY, Json(e)).into_response(),
            ServiceError::Server(e) => {
                let message = format!("Server error: {}", e);
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

/// A middleware function to identify the operator from a session.
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
        debug!("client session: {:?}", session);
        request.extensions_mut().insert(session);
        Ok(next.run(request).await)
    } else {
        Err(ServiceError::Unauthorized) // invalid token (session expired, etc.)
    }
}
