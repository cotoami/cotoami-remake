use std::{collections::HashMap, convert::Infallible};

use axum::{
    extract::State,
    http::StatusCode,
    response::{
        sse::{Event, KeepAlive, Sse},
        IntoResponse, Response,
    },
    routing::get,
    Json, Router,
};
use cotoami_db::prelude::*;
use futures::stream::Stream;
use serde_json::value::Value;
use tracing::error;
use validator::{Validate, ValidationError, ValidationErrors, ValidationErrorsKind};

use crate::AppState;

mod cotos;
mod nodes;

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", get(root))
        .route("/events", get(stream_events))
        .nest("/nodes", nodes::routes())
        .nest("/cotos", cotos::routes())
}

pub(super) async fn root(State(_): State<AppState>) -> &'static str { "Cotoami Node API" }

async fn stream_events(
    State(state): State<AppState>,
) -> Sse<impl Stream<Item = Result<Event, Infallible>>> {
    let sub = state.pubsub.lock().subscribe();
    Sse::new(sub).keep_alive(KeepAlive::default())
}

/////////////////////////////////////////////////////////////////////////////
// ApiError
/////////////////////////////////////////////////////////////////////////////

// A slightly revised version of the official example
// https://github.com/tokio-rs/axum/blob/v0.6.x/examples/anyhow-error-response/src/main.rs

enum ApiError {
    Server(anyhow::Error),
    Request(RequestError),
    Permission(PermissionError),
    Input(InputErrors),
    NotFound,
}

// Tell axum how to convert `ApiError` into a response.
impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        match self {
            ApiError::Server(e) => {
                let message = format!("Server error: {}", e);
                error!(message);
                (StatusCode::INTERNAL_SERVER_ERROR, message).into_response()
            }
            ApiError::Request(e) => (StatusCode::BAD_REQUEST, Json(e)).into_response(),
            ApiError::Permission(e) => (StatusCode::FORBIDDEN, Json(e)).into_response(),
            ApiError::Input(e) => (StatusCode::UNPROCESSABLE_ENTITY, Json(e)).into_response(),
            ApiError::NotFound => StatusCode::NOT_FOUND.into_response(),
        }
    }
}

// This enables using `?` on functions that return `Result<_, anyhow::Error>` to turn them into
// `Result<_, ApiError>`. That way you don't need to do that manually.
impl<E> From<E> for ApiError
where
    E: Into<anyhow::Error>,
{
    fn from(err: E) -> Self {
        let anyhow_err = err.into();
        match anyhow_err.downcast_ref::<DatabaseError>() {
            Some(DatabaseError::EntityNotFound { kind, id }) => ApiError::Input(
                InputError::new(kind.to_string(), "id", "not-found")
                    .with_param("value", Value::String(id.to_string()))
                    .into(),
            ),
            Some(DatabaseError::PermissionDenied { entity, id, op }) => ApiError::Permission(
                PermissionError::new(entity.to_string(), id.as_ref(), op.to_string()),
            ),
            _ => ApiError::Server(anyhow_err),
        }
    }
}

trait IntoApiResult<T> {
    fn into_result(self) -> Result<T, ApiError>;
}

/////////////////////////////////////////////////////////////////////////////
// ApiError / RequestError
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct RequestError {
    code: String,
}

impl RequestError {
    fn new(code: impl Into<String>) -> Self { Self { code: code.into() } }
}

impl<T> IntoApiResult<T> for RequestError {
    fn into_result(self) -> Result<T, ApiError> { Err(ApiError::Request(self)) }
}

/////////////////////////////////////////////////////////////////////////////
// ApiError / PermissionError
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct PermissionError {
    resource: String,
    id: Option<String>,
    op: String,
}

impl PermissionError {
    fn new(
        resource: impl Into<String>,
        id: Option<impl Into<String>>,
        op: impl Into<String>,
    ) -> Self {
        Self {
            resource: resource.into(),
            id: id.map(Into::into),
            op: op.into(),
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// ApiError / InputErrors
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct InputErrors(Vec<InputError>);

fn into_result<T, E>(e: E) -> Result<T, ApiError>
where
    E: Into<InputErrors>,
{
    Err(ApiError::Input(e.into()))
}

/// Create an [InputErrors] from a resource name and [ValidationErrors] as a tuple.
impl From<(&str, ValidationErrors)> for InputErrors {
    fn from((resource, src): (&str, ValidationErrors)) -> Self {
        let dst = src
            .into_errors()
            .into_iter()
            .map(|(field, errors_kind)| {
                // ignore Struct and List variants in ValidationErrorsKind for now
                if let ValidationErrorsKind::Field(errors) = errors_kind {
                    errors
                        .into_iter()
                        .map(|e| InputError::from_validation_error(resource, field, e))
                        .collect()
                } else {
                    Vec::new()
                }
            })
            .flatten()
            .collect();
        InputErrors(dst)
    }
}

impl<T> IntoApiResult<T> for (&str, ValidationErrors) {
    fn into_result(self) -> Result<T, ApiError> { into_result(self) }
}

/////////////////////////////////////////////////////////////////////////////
// ApiError / InputErrors / InputError
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct InputError {
    resource: String,
    field: String,
    code: String,
    params: HashMap<String, Value>,
}

impl InputError {
    fn new(resource: impl Into<String>, field: impl Into<String>, code: impl Into<String>) -> Self {
        Self {
            resource: resource.into(),
            field: field.into(),
            code: code.into(),
            params: HashMap::default(),
        }
    }

    /// Create an [InputError] from a [validator::ValidationError]
    fn from_validation_error(resource: &str, field: &str, src: ValidationError) -> Self {
        let mut input_error = Self::new(resource, field, src.code);
        for (key, value) in src.params {
            input_error.insert_param(key, value);
        }
        input_error
    }

    fn insert_param(&mut self, key: impl Into<String>, value: Value) {
        self.params.insert(key.into(), value);
    }

    fn with_param(mut self, key: impl Into<String>, value: Value) -> Self {
        self.insert_param(key, value);
        self
    }
}

impl<T> IntoApiResult<T> for InputError {
    fn into_result(self) -> Result<T, ApiError> { into_result(self) }
}

impl From<InputError> for InputErrors {
    fn from(e: InputError) -> Self { Self(vec![e]) }
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
