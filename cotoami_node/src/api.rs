use crate::AppState;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::sse::{Event, KeepAlive, Sse};
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{Json, Router};
use futures::stream::Stream;
use std::convert::Infallible;
use tracing::error;
use validator::{ValidationErrors, ValidationErrorsKind};

mod nodes;

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", get(root))
        .route("/events", get(stream_events))
        .nest("/nodes", nodes::routes())
}

pub(super) async fn root(State(_): State<AppState>) -> &'static str {
    "Cotoami Node API"
}

async fn stream_events(
    State(state): State<AppState>,
) -> Sse<impl Stream<Item = Result<Event, Infallible>>> {
    let sub = state.pubsub.lock().subscribe();
    Sse::new(sub).keep_alive(KeepAlive::default())
}

/////////////////////////////////////////////////////////////////////////////
// WebError
/////////////////////////////////////////////////////////////////////////////

// A slightly revised version of the official example
// https://github.com/tokio-rs/axum/blob/v0.6.x/examples/anyhow-error-response/src/main.rs

enum WebError {
    ServerSide(anyhow::Error),
    ClientSide(ClientErrors),
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

/////////////////////////////////////////////////////////////////////////////
// ClientErrors
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct ClientErrors {
    description: String,
    errors: Vec<ClientError>,
}

impl ClientErrors {
    fn new(description: impl Into<String>) -> Self {
        Self {
            description: description.into(),
            errors: Vec::new(),
        }
    }

    fn from_validation_errors(resource: &str, v_errors: ValidationErrors) -> Self {
        let mut c_errors = ClientErrors::new("Validation failed");
        for (field, errors_kind) in v_errors.into_errors().into_iter() {
            if let ValidationErrorsKind::Field(f_errors) = errors_kind {
                for f_error in f_errors.into_iter() {
                    let c_error = ClientError::field(resource, field, f_error.code);
                    c_errors.errors.push(c_error);
                }
            }
            // It doesn't support Struct/List in ValidationErrorsKind
        }
        c_errors
    }

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

/////////////////////////////////////////////////////////////////////////////
// ClientError
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct ClientError {
    resource: String,
    field: Option<String>,
    code: String,
}

impl ClientError {
    fn resource(resource: impl Into<String>, code: impl Into<String>) -> Self {
        Self {
            resource: resource.into(),
            field: None,
            code: code.into(),
        }
    }

    fn field(
        resource: impl Into<String>,
        field: impl Into<String>,
        code: impl Into<String>,
    ) -> Self {
        Self {
            resource: resource.into(),
            field: Some(field.into()),
            code: code.into(),
        }
    }

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
