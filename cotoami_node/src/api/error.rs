use std::collections::HashMap;

use cotoami_db::prelude::*;
use serde_json::value::Value;
use validator::{ValidationError, ValidationErrors, ValidationErrorsKind};

/////////////////////////////////////////////////////////////////////////////
// ApiError
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub enum ApiError {
    Request(RequestError),
    Unauthorized,
    Permission,
    NotFound,
    Input(InputErrors),
    Server(String),
    Unknown(String),
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
            Some(DatabaseError::AuthenticationFailed) => {
                ApiError::Request(RequestError::new("authentication-failed"))
            }
            Some(DatabaseError::PermissionDenied) => ApiError::Permission,
            _ => ApiError::Server(anyhow_err.to_string()),
        }
    }
}

pub(crate) trait IntoApiResult<T> {
    fn into_result(self) -> Result<T, ApiError>;
}

/////////////////////////////////////////////////////////////////////////////
// ApiError / RequestError
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct RequestError {
    code: String,
    params: HashMap<String, Value>,
}

impl RequestError {
    pub fn new(code: impl Into<String>) -> Self {
        Self {
            code: code.into(),
            params: HashMap::default(),
        }
    }

    pub fn insert_param(&mut self, key: impl Into<String>, value: Value) {
        self.params.insert(key.into(), value);
    }

    pub fn with_param(mut self, key: impl Into<String>, value: Value) -> Self {
        self.insert_param(key, value);
        self
    }
}

impl<T> IntoApiResult<T> for RequestError {
    fn into_result(self) -> Result<T, ApiError> { Err(ApiError::Request(self)) }
}

/////////////////////////////////////////////////////////////////////////////
// ApiError / InputErrors
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct InputErrors(Vec<InputError>);

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
            .flat_map(|(field, errors_kind)| {
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

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub(crate) struct InputError {
    resource: String,
    field: String,
    code: String,
    params: HashMap<String, Value>,
}

impl InputError {
    pub fn new(
        resource: impl Into<String>,
        field: impl Into<String>,
        code: impl Into<String>,
    ) -> Self {
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

    pub fn insert_param(&mut self, key: impl Into<String>, value: Value) {
        self.params.insert(key.into(), value);
    }

    pub fn with_param(mut self, key: impl Into<String>, value: Value) -> Self {
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
