//! Node API interface based on [tower_service::Service].
//!
//! This module aims to provide Node API functionalities via a commonalized interface
//! decoupled from the underlying protocol.

use bytes::Bytes;
use cotoami_db::prelude::{Id, Node};
use derive_new::new;
use uuid::Uuid;

use crate::api::error::ApiError;

pub mod client;
mod server;

#[derive(Clone, serde::Serialize, serde::Deserialize)]
pub struct Request {
    id: Uuid,
    to: Id<Node>,
    body: RequestBody,
}

impl Request {
    pub fn new(to: Id<Node>, body: RequestBody) -> Self {
        Self {
            id: Uuid::new_v4(),
            to,
            body,
        }
    }
}

#[derive(Clone, serde::Serialize, serde::Deserialize, new)]
pub struct Response {
    id: Uuid,
    body: Result<Bytes, ApiError>,
}

#[derive(Clone, serde::Serialize, serde::Deserialize)]
pub enum RequestBody {
    LocalNode,
    ChunkOfChanges { from: i64 },
}
