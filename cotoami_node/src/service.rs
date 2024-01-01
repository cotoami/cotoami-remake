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

use std::sync::Arc;

use anyhow::{Context, Result};
use bytes::Bytes;
use cotoami_db::prelude::*;
use derive_new::new;
use dyn_clone::DynClone;
use futures::future::BoxFuture;
use serde::de::DeserializeOwned;
use thiserror::Error;
use tower_service::Service;
use uuid::Uuid;

pub mod error;
pub mod models;
pub mod pubsub;
pub mod service_ext;

use self::models::*;
pub use self::{
    error::ServiceError,
    pubsub::*,
    service_ext::{NodeServiceExt, RemoteNodeServiceExt},
};

/////////////////////////////////////////////////////////////////////////////
// Service
/////////////////////////////////////////////////////////////////////////////

pub trait NodeService:
    Service<Request, Response = Response, Error = anyhow::Error, Future = NodeServiceFuture>
    + Send
    + Sync
    + DynClone
{
    fn description(&self) -> &str;
}

pub trait RemoteNodeService: NodeService {
    fn set_session_token(&mut self, token: &str) -> Result<()>;
}

pub(crate) type NodeServiceFuture = BoxFuture<'static, Result<Response, anyhow::Error>>;

/////////////////////////////////////////////////////////////////////////////
// Request
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct Request {
    id: Uuid,

    /// The operator node that has sent this request.
    ///
    /// This field isn't meant to be sent from a client via network, instead should be
    /// set by a service provider that keeps track of who is the client.
    #[serde(skip_serializing, skip_deserializing)]
    from: Option<Arc<Operator>>,

    body: RequestBody,
}

impl Request {
    pub fn new(body: RequestBody) -> Self {
        Self {
            id: Uuid::new_v4(),
            from: None,
            body,
        }
    }

    pub fn id(&self) -> &Uuid { &self.id }

    pub fn set_from(&mut self, from: Arc<Operator>) { self.from = Some(from); }

    pub fn from_or_err(&self) -> Result<&Arc<Operator>, ServiceError> {
        self.from.as_ref().ok_or(ServiceError::Unauthorized)
    }

    pub fn body(self) -> RequestBody { self.body }
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub enum RequestBody {
    LocalNode,
    ChunkOfChanges {
        from: i64,
    },
    CreateClientNodeSession(CreateClientNodeSession),
    RecentCotos {
        cotonoma: Option<Id<Cotonoma>>,
        pagination: Pagination,
    },
    PostCoto {
        content: String,
        summary: Option<String>,
        post_to: Id<Cotonoma>,
    },
}

impl RequestBody {
    pub fn into_request(self) -> Request { Request::new(self) }
}

/////////////////////////////////////////////////////////////////////////////
// Response
/////////////////////////////////////////////////////////////////////////////

#[derive(derive_more::Debug, Clone, serde::Serialize, serde::Deserialize, new)]
pub struct Response {
    id: Uuid,

    #[debug(skip)]
    body: Result<Bytes, ServiceError>,
}

impl Response {
    pub fn id(&self) -> &Uuid { &self.id }

    pub fn message_pack<T: DeserializeOwned>(self) -> Result<T> {
        let bytes = self.body.map_err(BackendServiceError)?;
        rmp_serde::from_slice(&bytes).context("Invalid response body")
    }
}

#[derive(Error, Debug)]
#[error("Backend service error: {0:?}")]
pub struct BackendServiceError(ServiceError);
