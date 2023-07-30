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
use validator::{Validate, ValidationErrors, ValidationErrorsKind};

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
    ClientSide(ClientErrors),
}

// Tell axum how to convert `ApiError` into a response.
impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        match self {
            ApiError::Server(e) => {
                error!("Something went wrong: {}", e);
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    format!("Something went wrong: {}", e),
                )
                    .into_response()
            }
            ApiError::Request(e) => (StatusCode::BAD_REQUEST, Json(e)).into_response(),
            ApiError::Permission(e) => (StatusCode::FORBIDDEN, Json(e)).into_response(),
            ApiError::ClientSide(errors) => (StatusCode::BAD_REQUEST, Json(errors)).into_response(),
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
            Some(DatabaseError::EntityNotFound { kind, id }) => ApiError::ClientSide(
                ClientError::resource(kind, "not-found")
                    .with_param("id", Value::String(id.into()))
                    .into(),
            ),
            _ => ApiError::Server(anyhow_err),
        }
    }
}

#[derive(serde::Serialize)]
struct RequestError {
    code: String,
}

#[derive(serde::Serialize)]
struct PermissionError {
    resource: String,
    id: Option<String>,
    op: String,
}

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
                    let mut c_error = ClientError::field(resource, field, f_error.code);
                    for (key, value) in f_error.params {
                        c_error.insert_param(key, value);
                    }
                    c_errors.errors.push(c_error);
                }
            }
            // It doesn't support Struct/List variants in ValidationErrorsKind
        }
        c_errors
    }

    fn into_result<T>(self) -> Result<T, ApiError> { into_result(self) }
}

fn into_result<T, E>(e: E) -> Result<T, ApiError>
where
    E: Into<ClientErrors>,
{
    Err(ApiError::ClientSide(e.into()))
}

#[derive(serde::Serialize)]
struct ClientError {
    resource: String,
    field: Option<String>,
    code: String,
    params: HashMap<String, Value>,
}

impl ClientError {
    fn resource(resource: impl Into<String>, code: impl Into<String>) -> Self {
        Self {
            resource: resource.into(),
            field: None,
            code: code.into(),
            params: HashMap::default(),
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
            params: HashMap::default(),
        }
    }

    fn insert_param(&mut self, key: impl Into<String>, value: Value) {
        self.params.insert(key.into(), value);
    }

    fn with_param(mut self, key: impl Into<String>, value: Value) -> Self {
        self.insert_param(key, value);
        self
    }

    fn into_result<T>(self) -> Result<T, ApiError> { into_result(self) }
}

impl From<ClientError> for ClientErrors {
    fn from(e: ClientError) -> Self {
        Self {
            description: if let Some(field) = e.field.as_deref() {
                format!("{}/{}: {}", e.resource, field, e.code)
            } else {
                format!("{}: {}", e.resource, e.code)
            },
            errors: vec![e],
        }
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
