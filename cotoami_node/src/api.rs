use crate::AppState;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::Router;
use tracing::error;

mod nodes;

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", get(root))
        .nest("/nodes", nodes::routes())
}

pub(super) async fn root(State(_): State<AppState>) -> &'static str {
    "Hello, World!"
}

/////////////////////////////////////////////////////////////////////////////
// Handler's error
/////////////////////////////////////////////////////////////////////////////

// A slightly revised version of the official example
// https://github.com/tokio-rs/axum/blob/v0.6.x/examples/anyhow-error-response/src/main.rs

enum WebError {
    AppError(anyhow::Error),
    Status((StatusCode, String)),
}

// Tell axum how to convert `WebError` into a response.
impl IntoResponse for WebError {
    fn into_response(self) -> Response {
        match self {
            WebError::AppError(e) => {
                error!("Something went wrong: {}", e);
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    format!("Something went wrong: {}", e),
                )
                    .into_response()
            }
            WebError::Status(status) => status.into_response(),
        }
    }
}

// This enables using `?` on functions that return `Result<_, anyhow::Error>` to turn them into
// `Result<_, WebError>`. That way you don't need to do that manually.
impl<E> From<E> for WebError
where
    E: Into<anyhow::Error>,
{
    fn from(err: E) -> Self {
        WebError::AppError(err.into())
    }
}
