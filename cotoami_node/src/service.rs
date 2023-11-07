//! Node API interface based on [tower_service::Service].
//!
//! This module aims to provide Node API functionalities via a commonalized interface
//! decoupled from the underlying protocol.

use uuid::Uuid;

use crate::api::error::ApiError;

mod server;

#[derive(serde::Serialize, serde::Deserialize)]
pub struct Request {
    id: Uuid,
    body: RequestBody,
}

#[derive(serde::Serialize, serde::Deserialize)]
pub struct Response {
    id: Uuid,
    body: Result<Vec<u8>, ApiError>,
}

#[derive(serde::Serialize, serde::Deserialize)]
pub enum RequestBody {
    GetLocalNode,
}
