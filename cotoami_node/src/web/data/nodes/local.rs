use std::sync::Arc;

use axum::{
    extract::{Json, State},
    routing::{get, put},
    Extension, Router,
};
use axum_extra::TypedHeader;
use cotoami_db::prelude::*;

use crate::{
    service::{models::LocalServer, ServiceError},
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(local_node))
        .route("/server", get(local_server))
        .route("/icon", put(set_local_node_icon))
        .route("/image-max-size", put(set_image_max_size))
        .route("/enable-anonymous", put(enable_anonymous_read))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/local
/////////////////////////////////////////////////////////////////////////////

async fn local_node(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
) -> Result<Content<Node>, ServiceError> {
    state.local_node().await.map(|node| Content(node, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/local/server
/////////////////////////////////////////////////////////////////////////////

async fn local_server(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
) -> Result<Content<LocalServer>, ServiceError> {
    state
        .local_server(Arc::new(operator))
        .map(|server| Content(server, accept))
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
// PUT /api/data/nodes/local/image-max-size
/////////////////////////////////////////////////////////////////////////////

async fn set_image_max_size(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Json(size): Json<i32>,
) -> Result<Content<LocalNode>, ServiceError> {
    state
        .set_image_max_size(size, Arc::new(operator))
        .await
        .map(|local| Content(local, accept))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/nodes/local/enable-anonymous
/////////////////////////////////////////////////////////////////////////////

async fn enable_anonymous_read(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Json(enable): Json<bool>,
) -> Result<Content<LocalNode>, ServiceError> {
    state
        .enable_anonymous_read(enable, Arc::new(operator))
        .await
        .map(|local| Content(local, accept))
}
