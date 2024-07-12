use axum::{
    extract::{Path, State},
    routing::get,
    Extension, Router, TypedHeader,
};
use cotoami_db::prelude::*;

use crate::{
    service::{models::NodeDetails, ServiceError},
    state::NodeState,
    web::{Accept, Content},
};

mod children;
mod clients;
mod cotonomas;
mod cotos;
mod parents;
mod servers;

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/local", get(local_node))
        .route("/:node_id/details", get(get_node_details))
        .nest("/:node_id/cotonomas", cotonomas::routes())
        .nest("/:node_id/cotos", cotos::routes())
        .nest("/servers", servers::routes())
        .nest("/clients", clients::routes())
        .nest("/parents", parents::routes())
        .nest("/children", children::routes())
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/:node_id/details
/////////////////////////////////////////////////////////////////////////////

async fn get_node_details(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(node_id): Path<Id<Node>>,
) -> Result<Content<NodeDetails>, ServiceError> {
    state
        .node_details(node_id)
        .await
        .map(|details| Content(details, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/local
/////////////////////////////////////////////////////////////////////////////

async fn local_node(
    State(state): State<NodeState>,
    Extension(_operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
) -> Result<Content<Node>, ServiceError> {
    state.local_node().await.map(|x| Content(x, accept))
}
