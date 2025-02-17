use std::sync::Arc;

use axum::{
    extract::{Json, Path, State},
    routing::{get, put},
    Extension, Router,
};
use axum_extra::TypedHeader;
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
        .route("/local/icon", put(set_local_node_icon))
        .route("/local/enable-anonymous", put(enable_anonymous_read))
        .route("/{node_id}/details", get(node_details))
        .nest("/{node_id}/cotonomas", cotonomas::routes())
        .nest("/{node_id}/cotos", cotos::routes())
        .nest("/servers", servers::routes())
        .nest("/clients", clients::routes())
        .nest("/parents", parents::routes())
        .nest("/children", children::routes())
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/:node_id/details
/////////////////////////////////////////////////////////////////////////////

async fn node_details(
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
    state.local_node().await.map(|node| Content(node, accept))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/nodes/local/icon
/////////////////////////////////////////////////////////////////////////////

async fn set_local_node_icon(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    body: axum::body::Bytes,
) -> Result<Content<Node>, ServiceError> {
    state
        .set_local_node_icon(body, Arc::new(operator))
        .await
        .map(|node| Content(node, accept))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/nodes/local/enable-anonymous
/////////////////////////////////////////////////////////////////////////////

async fn enable_anonymous_read(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Json(enable): Json<bool>,
) -> Result<Content<bool>, ServiceError> {
    state
        .enable_anonymous_read(enable, Arc::new(operator))
        .await
        .map(|enabled| Content(enabled, accept))
}
