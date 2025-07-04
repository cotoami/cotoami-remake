use std::{collections::HashMap, sync::Arc};

use axum::{
    extract::{Extension, Path, State},
    routing::{get, put},
    Router,
};
use axum_extra::TypedHeader;
use chrono::NaiveDateTime;
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
mod local;
mod servers;

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/others-last-posted-at", get(others_last_posted_at))
        .route("/mark-as-read", put(mark_all_as_read))
        .route("/{node_id}/details", get(node_details))
        .route("/{node_id}/mark-as-read", put(mark_as_read))
        .nest("/{node_id}/cotonomas", cotonomas::routes())
        .nest("/{node_id}/cotos", cotos::routes())
        .nest("/local", local::routes())
        .nest("/servers", servers::routes())
        .nest("/clients", clients::routes())
        .nest("/children", children::routes())
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/others-last-posted-at
/////////////////////////////////////////////////////////////////////////////

async fn others_last_posted_at(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
) -> Result<Content<HashMap<Id<Node>, NaiveDateTime>>, ServiceError> {
    state
        .others_last_posted_at(Arc::new(operator))
        .await
        .map(|map| Content(map, accept))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/nodes/mark-as-read
/////////////////////////////////////////////////////////////////////////////

async fn mark_all_as_read(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
) -> Result<Content<NaiveDateTime>, ServiceError> {
    state
        .mark_all_as_read(Arc::new(operator))
        .await
        .map(|time| Content(time, accept))
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
// GET /api/data/nodes/:node_id/mark-as-read
/////////////////////////////////////////////////////////////////////////////

async fn mark_as_read(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(node_id): Path<Id<Node>>,
) -> Result<Content<NaiveDateTime>, ServiceError> {
    state
        .mark_as_read(node_id, Arc::new(operator))
        .await
        .map(|time| Content(time, accept))
}
