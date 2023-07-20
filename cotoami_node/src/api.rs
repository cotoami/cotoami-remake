use crate::AppState;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{Json, Router};
use derive_new::new;
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
// Errors
/////////////////////////////////////////////////////////////////////////////

// A slightly revised version of the official example
// https://github.com/tokio-rs/axum/blob/v0.6.x/examples/anyhow-error-response/src/main.rs

enum WebError {
    ServerSide(anyhow::Error),
    ClientSide(ClientErrors),
    Status((StatusCode, String)),
}

// Tell axum how to convert `WebError` into a response.
impl IntoResponse for WebError {
    fn into_response(self) -> Response {
        match self {
            WebError::ServerSide(e) => {
                error!("Something went wrong: {}", e);
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    format!("Something went wrong: {}", e),
                )
                    .into_response()
            }
            WebError::ClientSide(errors) => (StatusCode::BAD_REQUEST, Json(errors)).into_response(),
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
        WebError::ServerSide(err.into())
    }
}

#[derive(new, serde::Serialize)]
struct ClientErrors {
    description: String,
    errors: Vec<ClientError>,
}

impl ClientErrors {
    fn into_result<T>(self) -> Result<T, WebError> {
        into_result(self)
    }
}

fn into_result<T, E>(e: E) -> Result<T, WebError>
where
    E: Into<ClientErrors>,
{
    Err(WebError::ClientSide(e.into()))
}

#[derive(new, serde::Serialize)]
struct ClientError {
    resource: String,
    field: Option<String>,
    code: String,
}

impl ClientError {
    fn into_result<T>(self) -> Result<T, WebError> {
        into_result(self)
    }
}

impl From<ClientError> for ClientErrors {
    fn from(e: ClientError) -> Self {
        Self {
            description: if let Some(field) = e.field.as_deref() {
                format!("{} / {}: {}", e.resource, field, e.code)
            } else {
                format!("{}: {}", e.resource, e.code)
            },
            errors: vec![e],
        }
    }
}
