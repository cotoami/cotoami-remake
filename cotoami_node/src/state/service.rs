//! Server-side implemention of Node Service,
//! which is intended for non-HTTP protocols such as WebSocket.

use anyhow::Result;
use bytes::Bytes;
use cotoami_db::prelude::*;
use futures::future::FutureExt;
use serde_json::value::Value;

use crate::{
    service::{error::InputError, NodeServiceFuture, *},
    state::NodeState,
};

mod changes;
mod cotonomas;
mod cotos;
mod nodes;
mod session;

/////////////////////////////////////////////////////////////////////////////
// NodeService implemented for NodeState
/////////////////////////////////////////////////////////////////////////////

impl NodeState {
    async fn handle_request(self, request: Request) -> Result<Bytes, ServiceError> {
        let format = request.accept();
        let opr = request.try_auth().map(Clone::clone);
        match request.command() {
            Command::LocalNode => format.to_bytes(self.local_node().await),
            Command::ChunkOfChanges { from } => format.to_bytes(self.chunk_of_changes(from).await),
            Command::CreateClientNodeSession(input) => {
                format.to_bytes(self.create_client_node_session(input).await)
            }
            Command::RecentCotonomas { node, pagination } => {
                format.to_bytes(self.recent_cotonomas(node, pagination).await)
            }
            Command::Cotonoma { id } => format.to_bytes(self.cotonoma(id).await),
            Command::SubCotonomas { id, pagination } => {
                format.to_bytes(self.sub_cotonomas(id, pagination).await)
            }
            Command::RecentCotos {
                cotonoma,
                pagination,
            } => format.to_bytes(self.recent_cotos(cotonoma, pagination).await),
            Command::PostCoto {
                content,
                summary,
                post_to,
            } => format.to_bytes(self.post_coto(content, summary, post_to, opr?).await),
        }
    }
}

impl Service<Request> for NodeState {
    type Response = Response;
    type Error = anyhow::Error;
    type Future = NodeServiceFuture;

    fn call(&self, request: Request) -> Self::Future {
        let this = self.clone();
        async move {
            Ok(Response::new(
                *request.id(),
                request.accept(),
                this.handle_request(request).await,
            ))
        }
        .boxed()
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

        // DatabaseError
        match anyhow_err.downcast_ref::<DatabaseError>() {
            Some(DatabaseError::EntityNotFound { kind, id }) => {
                return InputError::new(kind.to_string(), "id", "not-found")
                    .with_param("value", Value::String(id.to_string()))
                    .into();
            }
            Some(DatabaseError::AuthenticationFailed) => {
                return Self::request("authentication-failed");
            }
            Some(DatabaseError::PermissionDenied) => return ServiceError::Permission,
            _ => (),
        }

        ServiceError::Server(anyhow_err.to_string())
    }
}
