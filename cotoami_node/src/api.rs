use crate::AppState;
use axum::extract::State;
use axum::http::Uri;
use axum::response::IntoResponse;

pub(super) mod nodes;

/// axum handler for any request that fails to match the router routes.
/// This implementation returns HTTP status code Not Found (404).
pub(super) async fn fallback(uri: Uri) -> impl IntoResponse {
    (
        axum::http::StatusCode::NOT_FOUND,
        format!("No route {}", uri),
    )
}

pub(super) async fn root(State(_): State<AppState>) -> &'static str {
    "Hello, World!"
}
