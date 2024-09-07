use std::collections::HashMap;

use serde_json::value::Value;
use validator::{ValidationError, ValidationErrors, ValidationErrorsKind};

/////////////////////////////////////////////////////////////////////////////
// ServiceError
/////////////////////////////////////////////////////////////////////////////

/// An error happened during service execution.
///
/// It doesn't implement [std::error::Error] because it has to be (de)serializable
/// in order to be sent to other layers sometimes over the wire.
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
    pub fn request(code: impl Into<String>, message: impl Into<String>) -> Self {
        RequestError::new(code, message).into()
    }
}

pub(crate) trait IntoServiceResult<T> {
    fn into_result(self) -> Result<T, ServiceError>;
}

/////////////////////////////////////////////////////////////////////////////
// ServiceError / RequestError
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct RequestError {
    pub code: String,
    pub default_message: String,
    pub params: HashMap<String, Value>,
}

impl RequestError {
    pub fn new(code: impl Into<String>, message: impl Into<String>) -> Self {
        Self {
            code: code.into(),
            default_message: message.into(),
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
impl From<ValidationErrors> for InputErrors {
    fn from(src: ValidationErrors) -> Self {
        let dst = src
            .into_errors()
            .into_iter()
            .flat_map(|(field, errors_kind)| {
                // TODO: ignore Struct and List variants in ValidationErrorsKind for now
                if let ValidationErrorsKind::Field(errors) = errors_kind {
                    errors
                        .into_iter()
                        .map(|e| InputError::from_validation_error(field, e))
                        .collect()
                } else {
                    Vec::new()
                }
            })
            .collect();
        InputErrors(dst)
    }
}

impl<T> IntoServiceResult<T> for ValidationErrors {
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
    field: String,
    code: String,
    message: Option<String>,
    params: HashMap<String, Value>,
}

impl InputError {
    pub fn new(
        field: impl Into<String>,
        code: impl Into<String>,
        message: Option<impl Into<String>>,
    ) -> Self {
        Self {
            field: field.into(),
            code: code.into(),
            message: message.map(Into::into),
            params: HashMap::default(),
        }
    }

    /// Create an [InputError] from a [validator::ValidationError]
    fn from_validation_error(field: &str, src: ValidationError) -> Self {
        let mut input_error = Self::new(field, src.code, src.message);
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
