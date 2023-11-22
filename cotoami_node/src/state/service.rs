//! Server-side implemention of Node Service,
//! which is intended for non-HTTP protocols such as WebSocket.

use std::{
    future::Future,
    pin::Pin,
    task::{Context, Poll},
};

use anyhow::Result;
use bytes::Bytes;
use cotoami_db::prelude::*;
use serde_json::value::Value;
use tower_service::Service;

use crate::{
    service::{
        error::{InputError, RequestError},
        *,
    },
    state::{error::NodeError, NodeState},
};

/////////////////////////////////////////////////////////////////////////////
// NodeService implemented for NodeState
/////////////////////////////////////////////////////////////////////////////

impl NodeState {
    async fn handle_request(self, request: Request) -> Result<Bytes> {
        match request.body() {
            RequestBody::LocalNode => self.local_node().await.and_then(Self::to_bytes),
            RequestBody::ChunkOfChanges { from } => {
                self.chunk_of_changes(from).await.and_then(Self::to_bytes)
            }
            RequestBody::CreateClientNodeSession(input) => self
                .create_client_node_session(input)
                .await
                .and_then(Self::to_bytes),
        }
    }

    fn to_bytes<T: serde::Serialize>(t: T) -> Result<Bytes> {
        rmp_serde::to_vec(&t)
            .map(Bytes::from)
            .map_err(anyhow::Error::from)
    }
}

impl Service<Request> for NodeState {
    type Response = Response;
    type Error = anyhow::Error;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>> + Send>>;

    fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }

    fn call(&mut self, request: Request) -> Self::Future {
        let this = self.clone();
        Box::pin(async move {
            Ok(Response::new(
                *request.id(),
                this.handle_request(request)
                    .await
                    .map_err(ServiceError::from),
            ))
        })
    }
}

impl NodeService for NodeState {
    fn description(&self) -> &str { "local-node" }
}

/////////////////////////////////////////////////////////////////////////////
// Construct ServiceError from anyhow::Error
/////////////////////////////////////////////////////////////////////////////

impl<E> From<E> for ServiceError
where
    E: Into<anyhow::Error>,
{
    fn from(err: E) -> Self {
        let anyhow_err = err.into();

        // NodeError
        match anyhow_err.downcast_ref::<NodeError>() {
            Some(NodeError::WrongDatabaseRole) => {
                return ServiceError::Request(RequestError::new("wrong-database-role"))
            }
            _ => (),
        }

        // DatabaseError
        match anyhow_err.downcast_ref::<DatabaseError>() {
            Some(DatabaseError::EntityNotFound { kind, id }) => {
                return ServiceError::Input(
                    InputError::new(kind.to_string(), "id", "not-found")
                        .with_param("value", Value::String(id.to_string()))
                        .into(),
                )
            }
            Some(DatabaseError::AuthenticationFailed) => {
                return ServiceError::Request(RequestError::new("authentication-failed"))
            }
            Some(DatabaseError::PermissionDenied) => return ServiceError::Permission,
            _ => (),
        }

        ServiceError::Server(anyhow_err.to_string())
    }
}
