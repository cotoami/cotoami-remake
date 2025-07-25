//! Node API interface based on [Service] trait.
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

use std::{borrow::Cow, future::Future, sync::Arc};

use anyhow::{ensure, Context, Result};
use cotoami_db::{prelude::*, rmp_serde};
use derive_new::new;
use dyn_clone::DynClone;
use futures::future::BoxFuture;
use serde::de::DeserializeOwned;
use thiserror::Error;
use uuid::Uuid;

pub mod command;
pub mod error;
pub mod models;
pub mod pubsub;
pub mod service_ext;

pub(crate) use self::{
    command::*,
    error::*,
    models::*,
    pubsub::*,
    service_ext::{NodeServiceExt, RemoteNodeServiceExt},
};

/////////////////////////////////////////////////////////////////////////////
// Service
/////////////////////////////////////////////////////////////////////////////

/// An asynchronous function from a [Request] to a [Response].
///
/// It's kind of a simplified version of `tower::Service` modified to suit Cotoami's use cases.
///
/// Compared to tower, it doesn't have `poll_ready`, and `call` doesn't require `&mut self`,
/// which is sometimes inconvenient in concurrent situtations and especially doesn't go well with
/// Tauri's state management: (<https://docs.rs/tauri/1.6.1/tauri/trait.Manager.html#method.manage>).
/// If it requires `&mut self`, a `Service` in tauri state needs to be wrapped in `Mutex`
/// to invoke the method, which causes a problem in a tauri command since `MutexGuard` is `!Send`.
///
/// ## Error handling
///
/// [Service] returns an error in two ways:
///
/// 1. As a result of awaiting [Service::Future] returned by [Service::call].
///     * This error is caused during sending a request or receiving a response,
///       unrelated to the logic of a target service.
/// 2. A backend [ServiceError] that is saved in a [Response] and returned
///    when you try to get the body of a it.
///     * This [ServiceError] will be wrapped in [BackendServiceError] to allow you
///       to handle it as a [std::error::Error].
pub trait Service<Request> {
    type Response;
    type Error;
    type Future: Future<Output = Result<Self::Response, Self::Error>>;

    fn call(&self, request: Request) -> Self::Future;
}

pub trait NodeService:
    Service<Request, Response = Response, Error = anyhow::Error, Future = NodeServiceFuture>
    + Send
    + Sync
    + DynClone
{
    fn description(&self) -> Cow<str>;
}

pub trait RemoteNodeService: NodeService {
    fn set_session_token(&mut self, token: &str) -> Result<()>;
}

pub type NodeServiceFuture = BoxFuture<'static, Result<Response, anyhow::Error>>;

/////////////////////////////////////////////////////////////////////////////
// Request
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct Request {
    id: Uuid,

    /// The operator that has sent this request.
    ///
    /// This field isn't meant to be sent from a client via network, instead should be
    /// set by a service provider that keeps track of who is the client.
    #[serde(skip_serializing, skip_deserializing)]
    from: Option<Arc<Operator>>,

    accept: SerializeFormat,

    as_owner: bool,

    command: Command,
}

impl Request {
    pub fn new(command: Command) -> Self {
        Self {
            id: Uuid::new_v4(),
            from: None,
            accept: SerializeFormat::MessagePack,
            as_owner: false,
            command,
        }
    }

    pub fn id(&self) -> &Uuid { &self.id }

    pub fn set_from(&mut self, from: Arc<Operator>) { self.from = Some(from); }

    pub fn try_auth(&self) -> Result<&Arc<Operator>, ServiceError> {
        self.from.as_ref().ok_or(ServiceError::Unauthorized)
    }

    pub fn set_accept(&mut self, accept: SerializeFormat) { self.accept = accept }

    pub fn accept(&self) -> SerializeFormat { self.accept }

    pub fn as_owner(&self) -> bool { self.as_owner }

    pub fn operate_as_owner(&mut self) { self.as_owner = true }

    pub fn command(self) -> Command { self.command }
}

impl Command {
    pub fn into_request(self) -> Request { Request::new(self) }

    pub fn into_request_from(self, from: Arc<Operator>) -> Request {
        let mut request = Request::new(self);
        request.set_from(from);
        request
    }
}

/////////////////////////////////////////////////////////////////////////////
// Response
/////////////////////////////////////////////////////////////////////////////

#[derive(derive_more::Debug, Clone, serde::Serialize, serde::Deserialize, new)]
pub struct Response {
    id: Uuid,

    body_format: SerializeFormat,

    #[debug(skip)]
    body: Result<Bytes, ServiceError>,
}

impl Response {
    pub fn to(request: &Request, body: Result<Bytes, ServiceError>) -> Self {
        Response::new(*request.id(), request.accept(), body)
    }

    pub fn id(&self) -> &Uuid { &self.id }

    pub fn content<T: DeserializeOwned>(self) -> Result<T> {
        let bytes = self.body.map_err(BackendServiceError)?;
        match self.body_format {
            SerializeFormat::Json => {
                serde_json::from_slice(bytes.as_ref()).context("Invalid JSON bytes")
            }
            SerializeFormat::MessagePack => {
                rmp_serde::from_slice(bytes.as_ref()).context("Invalid MessagePack bytes")
            }
        }
    }

    pub fn json(self) -> Result<String> {
        ensure!(
            matches!(self.body_format, SerializeFormat::Json),
            "Response body format is not JSON."
        );

        let bytes = self.body.map_err(BackendServiceError)?;
        std::str::from_utf8(bytes.as_ref())
            .map(Into::into)
            .map_err(anyhow::Error::from)
    }
}

#[derive(Debug, Clone, Copy, serde::Serialize, serde::Deserialize)]
pub enum SerializeFormat {
    Json,
    MessagePack,
}

impl SerializeFormat {
    pub(crate) fn serialize<T: serde::Serialize>(
        &self,
        result: Result<T, ServiceError>,
    ) -> Result<Bytes, ServiceError> {
        result.and_then(|t| match self {
            SerializeFormat::Json => serde_json::to_vec(&t)
                .map(Bytes::from)
                .map_err(ServiceError::from),
            SerializeFormat::MessagePack => rmp_serde::to_vec(&t)
                .map(Bytes::from)
                .map_err(ServiceError::from),
        })
    }
}

#[derive(Error, Debug)]
#[error("Backend service error: {0:?}")]
pub struct BackendServiceError(pub ServiceError);
