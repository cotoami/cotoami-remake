//! Server-side implemention of Node Service,
//! which is intended for non-HTTP protocols such as WebSocket.

use std::sync::Arc;

use anyhow::Result;
use bytes::Bytes;
use cotoami_db::prelude::*;
use futures::future::{BoxFuture, FutureExt};
use serde_json::json;
use tokio::task::spawn_blocking;

use crate::{
    service::{
        error::{IntoServiceResult, RequestError},
        NodeServiceFuture, *,
    },
    state::NodeState,
};

mod changes;
mod cotonomas;
mod cotos;
mod graph;
mod nodes;
mod servers;
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
            Command::AddServerNode(input) => {
                format.to_bytes(self.add_server_node(input, opr?).await)
            }
            Command::RecentCotonomas { node, pagination } => {
                format.to_bytes(self.recent_cotonomas(node, pagination).await)
            }
            Command::Cotonoma { id } => format.to_bytes(self.cotonoma(id).await),
            Command::CotonomaDetails { id } => format.to_bytes(self.cotonoma_details(id).await),
            Command::CotonomaByName { name, node } => {
                format.to_bytes(self.cotonoma_by_name(name, node).await)
            }
            Command::SubCotonomas { id, pagination } => {
                format.to_bytes(self.sub_cotonomas(id, pagination).await)
            }
            Command::RecentCotos {
                node,
                cotonoma,
                pagination,
            } => format.to_bytes(self.recent_cotos(node, cotonoma, pagination).await),
            Command::SearchCotos {
                query,
                node,
                cotonoma,
                pagination,
            } => format.to_bytes(self.search_cotos(query, node, cotonoma, pagination).await),
            Command::GraphFromCoto { coto } => format.to_bytes(self.graph_from_coto(coto).await),
            Command::GraphFromCotonoma { cotonoma } => {
                format.to_bytes(self.graph_from_cotonoma(cotonoma).await)
            }
            Command::PostCoto { input, post_to } => {
                format.to_bytes(self.post_coto(input, post_to, opr?).await)
            }
            Command::PostCotonoma { input, post_to } => {
                format.to_bytes(self.post_cotonoma(input, post_to, opr?).await)
            }
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
            Some(e @ DatabaseError::EntityNotFound { .. }) => {
                return ServiceError::NotFound(Some(e.to_string()));
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

/////////////////////////////////////////////////////////////////////////////
// Utils for service implementations
/////////////////////////////////////////////////////////////////////////////

impl NodeState {
    pub(crate) async fn change<Input, Change, Apply, Forward>(
        self,
        input: Input,
        cotonoma: Id<Cotonoma>,
        operator: Arc<Operator>,
        apply: Apply,
        forward: Forward,
    ) -> Result<Change, ServiceError>
    where
        Input: Send + 'static,
        Change: Send + 'static,
        Apply: FnOnce(
                &mut DatabaseSession<'_>,
                Input,
                &Cotonoma,
                &Operator,
            ) -> Result<(Change, ChangelogEntry)>
            + Send
            + 'static,
        Forward: for<'a> FnOnce(
            &'a mut dyn NodeService,
            Input,
            &Cotonoma,
        ) -> BoxFuture<'a, Result<Change, anyhow::Error>>,
    {
        let result = spawn_blocking({
            let this = self.clone();
            move || {
                let mut ds = this.db().new_session()?;
                let (cotonoma, _) = ds.try_get_cotonoma(&cotonoma)?;
                if this.db().globals().is_local(&cotonoma) {
                    let (change, log) = apply(&mut ds, input, &cotonoma, operator.as_ref())?;
                    this.pubsub().publish_change(log);
                    Ok::<_, anyhow::Error>(ChangeResult::Changed(change))
                } else {
                    Ok::<_, anyhow::Error>(ChangeResult::ToForward { cotonoma, input })
                }
            }
        })
        .await??;

        match result {
            ChangeResult::Changed(change) => Ok(change),
            ChangeResult::ToForward { cotonoma, input } => {
                if let Some(mut parent_service) = self.parent_service(&cotonoma.node_id) {
                    forward(&mut *parent_service, input, &cotonoma)
                        .await
                        .map_err(ServiceError::from)
                } else {
                    read_only_cotonoma_error(&cotonoma.name).into_result()
                }
            }
        }
    }
}

enum ChangeResult<Input, Change> {
    Changed(Change),
    ToForward { cotonoma: Cotonoma, input: Input },
}

fn read_only_cotonoma_error(cotonoma_name: &str) -> RequestError {
    RequestError::new("read-only-cotonoma").with_param("cotonoma-name", json!(cotonoma_name))
}
