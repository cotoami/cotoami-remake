//! [NodeService] implementation based on [NodeState].

use std::sync::Arc;

use anyhow::Result;
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
mod session;

/////////////////////////////////////////////////////////////////////////////
// NodeService implemented for NodeState
/////////////////////////////////////////////////////////////////////////////

impl NodeState {
    async fn handle_request(self, request: Request) -> Result<Bytes, ServiceError> {
        let format = request.accept();
        let opr = request.try_auth().map(Clone::clone);
        match request.command() {
            Command::LocalNode => format.serialize(self.local_node().await),
            Command::InitialDataset => format.serialize(self.initial_dataset(opr?).await),
            Command::ChunkOfChanges { from } => format.serialize(self.chunk_of_changes(from).await),
            Command::SetLocalNodeIcon { icon } => {
                format.serialize(self.set_local_node_icon(icon.into(), opr?).await)
            }
            Command::NodeDetails { id } => format.serialize(self.node_details(id).await),
            Command::CreateClientNodeSession(input) => {
                format.serialize(self.create_client_node_session(input).await)
            }
            Command::TryLogIntoServer(input) => format.serialize(
                self.log_into_server(input)
                    .await
                    .map(|(session, _)| session),
            ),
            Command::AddServer(input) => format.serialize(self.add_server(input, opr?).await),
            Command::UpdateServer { id, values } => {
                format.serialize(self.update_server(id, values, opr?).await)
            }
            Command::RecentClients { pagination } => {
                format.serialize(self.recent_clients(pagination, opr?).await)
            }
            Command::ClientNode { id } => format.serialize(self.client_node(id, opr?).await),
            Command::AddClient(input) => format.serialize(self.add_client(input, opr?).await),
            Command::UpdateClient { id, values } => {
                format.serialize(self.update_client(id, values, opr?).await)
            }
            Command::RecentCotonomas { node, pagination } => {
                format.serialize(self.recent_cotonomas(node, pagination).await)
            }
            Command::Cotonoma { id } => format.serialize(self.cotonoma(id).await),
            Command::CotonomaDetails { id } => format.serialize(self.cotonoma_details(id).await),
            Command::CotonomaByName { name, node } => {
                format.serialize(self.cotonoma_by_name(name, node).await)
            }
            Command::CotonomasByPrefix { prefix, nodes } => {
                format.serialize(self.cotonomas_by_prefix(prefix, nodes).await)
            }
            Command::SubCotonomas { id, pagination } => {
                format.serialize(self.sub_cotonomas(id, pagination).await)
            }
            Command::RecentCotos {
                node,
                cotonoma,
                pagination,
            } => format.serialize(self.recent_cotos(node, cotonoma, pagination).await),
            Command::GeolocatedCotos { node, cotonoma } => {
                format.serialize(self.geolocated_cotos(node, cotonoma).await)
            }
            Command::CotosInGeoBounds {
                southwest,
                northeast,
            } => format.serialize(self.cotos_in_geo_bounds(southwest, northeast).await),
            Command::SearchCotos {
                query,
                node,
                cotonoma,
                pagination,
            } => format.serialize(self.search_cotos(query, node, cotonoma, pagination).await),
            Command::CotoDetails { id } => format.serialize(self.coto_details(id).await),
            Command::GraphFromCoto { coto } => format.serialize(self.graph_from_coto(coto).await),
            Command::GraphFromCotonoma { cotonoma } => {
                format.serialize(self.graph_from_cotonoma(cotonoma).await)
            }
            Command::PostCoto { input, post_to } => {
                format.serialize(self.post_coto(input, post_to, opr?).await)
            }
            Command::PostCotonoma { input, post_to } => {
                format.serialize(self.post_cotonoma(input, post_to, opr?).await)
            }
            Command::DeleteCoto { id } => format.serialize(self.delete_coto(id, opr?).await),
            Command::Repost { id, dest } => format.serialize(self.repost(id, dest, opr?).await),
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
                return Self::request("authentication-failed", "Authentication failed.");
            }
            Some(DatabaseError::PermissionDenied) => return ServiceError::Permission,
            Some(DatabaseError::NodeRoleConflict) => {
                return Self::request("invalid-node-role", "Couldn't attach the role to the node.");
            }
            _ => (),
        }

        // BackendServiceError
        match anyhow_err.downcast::<BackendServiceError>() {
            Ok(BackendServiceError(e)) => e,
            Err(e) => ServiceError::Server(e.to_string()),
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Utils for service implementations
/////////////////////////////////////////////////////////////////////////////

impl NodeState {
    pub(crate) async fn get<Value, Get>(&self, get: Get) -> Result<Value, ServiceError>
    where
        Value: Send + 'static,
        Get: FnOnce(&mut DatabaseSession<'_>) -> Result<Value> + Send + 'static,
    {
        let db = self.db().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;
            let value = get(&mut ds)?;
            Ok::<_, anyhow::Error>(value)
        })
        .await?
        .map_err(ServiceError::from)
    }

    pub(crate) async fn change<Change, Apply>(self, apply: Apply) -> Result<Change, ServiceError>
    where
        Change: Send + 'static,
        Apply:
            FnOnce(&mut DatabaseSession<'_>) -> Result<(Change, ChangelogEntry)> + Send + 'static,
    {
        spawn_blocking({
            move || {
                let mut ds = self.db().new_session()?;
                let (change, log) = apply(&mut ds)?;
                self.pubsub().publish_change(log);
                Ok(change)
            }
        })
        .await?
    }

    pub(crate) async fn change_in_cotonoma<Input, Change, Apply, Forward>(
        self,
        input: Input,
        cotonoma: Id<Cotonoma>,
        apply: Apply,
        forward: Forward,
    ) -> Result<Change, ServiceError>
    where
        Input: Send + 'static,
        Change: Send + 'static,
        Apply: FnOnce(&mut DatabaseSession<'_>, Input, &Cotonoma) -> Result<(Change, ChangelogEntry)>
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
                    let (change, log) = apply(&mut ds, input, &cotonoma)?;
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
                if let Some(mut parent_service) = self.parent_services().get(&cotonoma.node_id) {
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
    RequestError::new("read-only-cotonoma", "The cotonoma is read-only for you.")
        .with_param("cotonoma-name", json!(cotonoma_name))
}

/////////////////////////////////////////////////////////////////////////////
// InitialDataset
/////////////////////////////////////////////////////////////////////////////

impl NodeState {
    pub async fn initial_dataset(
        &self,
        operator: Arc<Operator>,
    ) -> Result<InitialDataset, ServiceError> {
        // Get the last change number before retrieving database contents to
        // ensure them to be the same or newer version than the number.
        //
        // Since the number will be used as a base of accepting changes,
        // older contents lead to missing changes to be applied:
        // <older contents> -> missing -> <last number> -> changes to be applied
        //
        // On the other hand, newer contents cause duplicate changes to be applied,
        // but it should be no problem as long as each change is idempotent:
        // <last number> -> duplicate changes to be applied -> <newer contents>
        let last_change_number = self.last_change_number().await?.unwrap_or(0);
        Ok(InitialDataset {
            last_change_number,
            nodes: self.all_nodes().await?,
            local_node_id: self.db().globals().try_get_local_node_id()?,
            parent_node_ids: self.db().globals().parent_node_ids(),
            servers: self.all_servers(operator).await?,
            active_clients: self.active_clients(),
        })
    }
}
