//! Node API interface based on [tower_service::Service].
//!
//! This module aims to provide Node API functionalities via a commonalized interface
//! decoupled from the underlying protocol. With this abstraction, the parent/child concept
//! is separated from network roles such as server/client or protocols.
//!
//! Possible implementations of the interface are:
//!
//! * Server-side
//!     * An interface for non-HTTP protocols (ex. WebSocket)
//! * Client-side
//!     * via plain HTTP request/response
//!     * via Server-Sent Events/HTTP request (reversal of client/server)
//!     * via WebSocket

use anyhow::Result;
use bytes::Bytes;
use derive_new::new;
use serde::de::DeserializeOwned;
use thiserror::Error;
use tower_service::Service;
use uuid::Uuid;

pub mod error;
pub mod models;
pub mod service_ext;

pub use self::{
    error::ServiceError,
    models::*,
    service_ext::{NodeServiceExt, RemoteNodeServiceExt},
};

/////////////////////////////////////////////////////////////////////////////
// Service
/////////////////////////////////////////////////////////////////////////////

pub trait NodeService: Service<Request, Response = Response, Error = anyhow::Error>
where
    Self::Future: Send,
{
    fn description(&self) -> &str;
}

pub trait RemoteNodeService: NodeService
where
    Self::Future: Send,
{
    fn set_session_token(&mut self, token: &str) -> Result<()>;
}

/////////////////////////////////////////////////////////////////////////////
// Request
/////////////////////////////////////////////////////////////////////////////

#[derive(Clone, serde::Serialize, serde::Deserialize)]
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

    pub fn id(&self) -> &Uuid { &self.id }

    pub fn body(self) -> RequestBody { self.body }
}

#[derive(Clone, serde::Serialize, serde::Deserialize)]
pub enum RequestBody {
    LocalNode,
    ChunkOfChanges { from: i64 },
    CreateClientNodeSession(CreateClientNodeSession),
}

impl RequestBody {
    pub fn into_request(self) -> Request { Request::new(self) }
}

/////////////////////////////////////////////////////////////////////////////
// Response
/////////////////////////////////////////////////////////////////////////////

#[derive(Clone, serde::Serialize, serde::Deserialize, new)]
pub struct Response {
    id: Uuid,
    body: Result<Bytes, ServiceError>,
}

impl Response {
    pub fn message_pack<T: DeserializeOwned>(self) -> Result<T> {
        let bytes = self.body.map_err(ServiceStdError)?;
        rmp_serde::from_slice(&bytes).map_err(anyhow::Error::from)
    }
}

#[derive(Error, Debug)]
#[error("Service error: {0:?}")]
pub struct ServiceStdError(ServiceError);
