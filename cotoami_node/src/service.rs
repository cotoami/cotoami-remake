//! Node API interface based on [tower_service::Service].
//!
//! This module aims to provide Node API functionalities via a commonalized interface
//! decoupled from the underlying protocol.

use bytes::Bytes;
use derive_new::new;
use uuid::Uuid;

use crate::api::error::ApiError;

pub mod client;
mod server;

#[derive(serde::Serialize, serde::Deserialize)]
pub struct Request {
    id: Uuid,
    body: RequestBody,
}

impl Request {
    pub fn new(body: RequestBody) -> Self {
        Self {
            id: Uuid::new_v4(),
            body,
        }
    }
}

#[derive(serde::Serialize, serde::Deserialize, new)]
pub struct Response {
    id: Uuid,
    body: Result<Bytes, ApiError>,
}

#[derive(serde::Serialize, serde::Deserialize)]
pub enum RequestBody {
    GetLocalNode,
}
