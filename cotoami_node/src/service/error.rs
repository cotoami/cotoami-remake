use std::collections::HashMap;

use serde_json::value::Value;
use validator::{ValidationError, ValidationErrors, ValidationErrorsKind};

/////////////////////////////////////////////////////////////////////////////
// ServiceError
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub enum ServiceError {
    Request(RequestError),
    Unauthorized,
    Permission,
    NotFound(Option<String>),
    Input(InputErrors),
    Server(String),
    NotImplemented,
    Unknown(String),
}

impl ServiceError {
    pub fn request(code: impl Into<String>) -> Self { RequestError::new(code).into() }
}

pub(crate) trait IntoServiceResult<T> {
    fn into_result(self) -> Result<T, ServiceError>;
}

/////////////////////////////////////////////////////////////////////////////
// ServiceError / RequestError
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

impl<T> IntoServiceResult<T> for RequestError {
    fn into_result(self) -> Result<T, ServiceError> { Err(ServiceError::Request(self)) }
}

impl From<RequestError> for ServiceError {
    fn from(e: RequestError) -> Self { ServiceError::Request(e) }
}

/////////////////////////////////////////////////////////////////////////////
// ServiceError / InputErrors
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct InputErrors(Vec<InputError>);

fn into_result<T, E>(e: E) -> Result<T, ServiceError>
where
    E: Into<InputErrors>,
{
    Err(ServiceError::Input(e.into()))
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

impl<T> IntoServiceResult<T> for (&str, ValidationErrors) {
    fn into_result(self) -> Result<T, ServiceError> { into_result(self) }
}

impl From<InputErrors> for ServiceError {
    fn from(e: InputErrors) -> Self { ServiceError::Input(e) }
}

/////////////////////////////////////////////////////////////////////////////
// ServiceError / InputErrors / InputError
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
}

impl<T> IntoServiceResult<T> for InputError {
    fn into_result(self) -> Result<T, ServiceError> { into_result(self) }
}

impl From<InputError> for InputErrors {
    fn from(e: InputError) -> Self { Self(vec![e]) }
}

impl From<InputError> for ServiceError {
    fn from(e: InputError) -> Self { InputErrors::from(e).into() }
}
