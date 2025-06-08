use std::{collections::HashMap, string::ToString};

use cotoami_db::prelude::Node;
use cotoami_node::prelude::*;
use serde_json::value::Value;

#[derive(serde::Serialize)]
pub struct Error {
    code: String,
    default_message: String,
    params: HashMap<String, Value>,
}

impl Error {
    pub fn new(code: impl Into<String>, message: impl Into<String>) -> Self {
        Self {
            code: code.into(),
            default_message: message.into(),
            params: HashMap::new(),
        }
    }

    pub fn with_param(mut self, name: impl Into<String>, value: Value) -> Self {
        self.params.insert(name.into(), value);
        self
    }

    pub fn invalid_owner_password(node: Node) -> Self {
        Error::new("invalid-owner-password", "Invalid owner password.")
            .with_param("node", serde_json::to_value(node).unwrap())
    }

    pub fn system_error(message: impl Into<String>) -> Self {
        Error::new("system-error", message.into())
    }

    // TODO: write thorough conversion
    fn from_service_error(e: ServiceError) -> Self {
        match e {
            ServiceError::Request(e) => e.into(),
            ServiceError::Unauthorized => {
                Error::new("authentication-failed", "Authentication failed.")
            }
            ServiceError::Permission => Error::new("permission-error", "Permission error."),
            ServiceError::NotFound(msg) => Error::new(
                "not-found",
                msg.unwrap_or("The requested resource is not found.".into()),
            ),
            ServiceError::Server(msg) => Error::new("server-error", msg),
            _ => Error::new("service-error", format!("{e:?}")),
        }
    }
}

impl From<anyhow::Error> for Error {
    fn from(e: anyhow::Error) -> Self {
        match e.downcast::<BackendServiceError>() {
            Ok(BackendServiceError(service_error)) => Self::from_service_error(service_error),
            Err(e) => Self::system_error(e.to_string()),
        }
    }
}

impl From<tauri::Error> for Error {
    fn from(e: tauri::Error) -> Self { Error::new("tauri-error", e.to_string()) }
}

impl From<ServiceError> for Error {
    fn from(e: ServiceError) -> Self { Self::from_service_error(e) }
}

impl From<RequestError> for Error {
    fn from(e: RequestError) -> Self {
        Self {
            code: e.code,
            default_message: e.default_message,
            params: e.params,
        }
    }
}
