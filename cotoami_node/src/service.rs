//! Node API interface based on [tower_service::Service].
//!
//! This module aims to provide Node API functionalities via a commonalized interface
//! decoupled from the underlying protocol. With this abstraction, the parent/child concept
//! is separated from network concepts and roles such as server/client or protocols.
//!
//! Possible implementations of the interface are:
//!
//! * Server-side
//!     * An interface for non-HTTP protocols (ex. WebSocket)
//! * Client-side
//!     * via plain HTTP request/response
//!     * via Server-Sent Events/HTTP request (reversal of client/server)
//!     * via WebSocket

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

    pub fn to(&self) -> &Id<Node> { &self.to }
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
