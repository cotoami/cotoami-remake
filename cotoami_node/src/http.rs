use axum::{
    http::{header::HeaderName, Request, StatusCode},
    middleware::Next,
    response::{IntoResponse, Response},
    Extension, Json,
};
use axum_extra::extract::cookie::{Cookie, CookieJar};
use tokio::task::spawn_blocking;
use tracing::{debug, error};
use validator::Validate;

use crate::{api::error::ApiError, AppState};

pub(crate) mod changes;
mod cotonomas;
mod cotos;
mod events;
mod nodes;
pub(crate) mod router;
pub(crate) mod session;

pub(super) use router::router;

/////////////////////////////////////////////////////////////////////////////
// Error
/////////////////////////////////////////////////////////////////////////////

// Tell axum how to convert `ApiError` into a response.
impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        match self {
            ApiError::Request(e) => (StatusCode::BAD_REQUEST, Json(e)).into_response(),
            ApiError::Unauthorized => StatusCode::UNAUTHORIZED.into_response(),
            ApiError::Permission => StatusCode::FORBIDDEN.into_response(),
            ApiError::NotFound => StatusCode::NOT_FOUND.into_response(),
            ApiError::Input(e) => (StatusCode::UNPROCESSABLE_ENTITY, Json(e)).into_response(),
            ApiError::Server(e) => {
                let message = format!("Server error: {}", e);
                error!(message);
                (StatusCode::INTERNAL_SERVER_ERROR, message).into_response()
            }
            ApiError::Unknown(_) => unreachable!(),
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
    Extension(state): Extension<AppState>,
    // CookieJar extractor will never reject a request
    // https://docs.rs/axum-extra/0.7.5/src/axum_extra/extract/cookie/mod.rs.html#96
    // https://docs.rs/axum/latest/axum/extract/index.html#optional-extractors
    jar: CookieJar,
    mut request: Request<B>,
    next: Next<B>,
) -> Result<Response, ApiError> {
    let cookie_value = jar.get(SESSION_COOKIE_NAME).map(Cookie::value);
    let header_value = request
        .headers()
        .get(SESSION_HEADER_NAME)
        .and_then(|v| v.to_str().ok());

    let token = if let Some(token) = cookie_value.or(header_value) {
        token.to_string() // create an owned string to be used in spawn_blocking
    } else {
        return Err(ApiError::Unauthorized); // missing session token
    };

    let operator = spawn_blocking(move || {
        let mut db = state.db.new_session()?;
        // https://rust-lang.github.io/async-book/07_workarounds/02_err_in_async_blocks.html
        Ok::<_, ApiError>(db.operator_in_session(&token)?)
    })
    .await??;

    if let Some(operator) = operator {
        debug!("Operator in session: {:?}", operator);
        request.extensions_mut().insert(operator);
        Ok(next.run(request).await)
    } else {
        Err(ApiError::Unauthorized) // invalid token (session expired, etc.)
    }
}

/////////////////////////////////////////////////////////////////////////////
// Pagination Query
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct Pagination {
    #[serde(default)]
    page: i64,

    #[validate(range(min = 1, max = 1000))]
    page_size: Option<i64>,
}
