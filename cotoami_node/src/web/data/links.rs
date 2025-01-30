use std::sync::Arc;

use anyhow::Result;
use axum::{
    extract::{Json, Path, State},
    routing::{get, post, put},
    Extension, Router,
};
use axum_extra::TypedHeader;
use cotoami_db::prelude::*;

use crate::{
    service::ServiceError,
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", post(connect))
        .route("/{link_id}", get(link).put(edit_link).delete(disconnect))
        .route("/{link_id}/order", put(change_order))
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/data/links
/////////////////////////////////////////////////////////////////////////////

async fn connect(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Json(input): Json<LinkInput<'static>>,
) -> Result<Content<Link>, ServiceError> {
    state
        .connect(input, Arc::new(operator))
        .await
        .map(|link| Content(link, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/links/{link_id}
/////////////////////////////////////////////////////////////////////////////

async fn link(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(link_id): Path<Id<Link>>,
) -> Result<Content<Link>, ServiceError> {
    state.link(link_id).await.map(|link| Content(link, accept))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/links/{link_id}
/////////////////////////////////////////////////////////////////////////////

async fn edit_link(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(link_id): Path<Id<Link>>,
    Json(diff): Json<LinkContentDiff<'static>>,
) -> Result<Content<Link>, ServiceError> {
    state
        .edit_link(link_id, diff, Arc::new(operator))
        .await
        .map(|link| Content(link, accept))
}

/////////////////////////////////////////////////////////////////////////////
// DELETE /api/data/links/{link_id}
/////////////////////////////////////////////////////////////////////////////

async fn disconnect(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(link_id): Path<Id<Link>>,
) -> Result<Content<Id<Link>>, ServiceError> {
    state
        .disconnect(link_id, Arc::new(operator))
        .await
        .map(|link_id| Content(link_id, accept))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/links/{link_id}/order
/////////////////////////////////////////////////////////////////////////////

async fn change_order(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(link_id): Path<Id<Link>>,
    Json(new_order): Json<i32>,
) -> Result<Content<Link>, ServiceError> {
    state
        .change_link_order(link_id, new_order, Arc::new(operator))
        .await
        .map(|link| Content(link, accept))
}
