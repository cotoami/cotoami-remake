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
        .route("/", post(create_ito))
        .route("/{ito_id}", get(ito).put(edit_ito).delete(delete_ito))
        .route("/{ito_id}/order", put(change_order))
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/data/itos
/////////////////////////////////////////////////////////////////////////////

async fn create_ito(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Json(input): Json<ItoInput<'static>>,
) -> Result<Content<Ito>, ServiceError> {
    state
        .create_ito(input, Arc::new(operator))
        .await
        .map(|ito| Content(ito, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/itos/{ito_id}
/////////////////////////////////////////////////////////////////////////////

async fn ito(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(ito_id): Path<Id<Ito>>,
) -> Result<Content<Ito>, ServiceError> {
    state.ito(ito_id).await.map(|ito| Content(ito, accept))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/itos/{ito_id}
/////////////////////////////////////////////////////////////////////////////

async fn edit_ito(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(ito_id): Path<Id<Ito>>,
    Json(diff): Json<ItoContentDiff<'static>>,
) -> Result<Content<Ito>, ServiceError> {
    state
        .edit_ito(ito_id, diff, Arc::new(operator))
        .await
        .map(|ito| Content(ito, accept))
}

/////////////////////////////////////////////////////////////////////////////
// DELETE /api/data/itos/{ito_id}
/////////////////////////////////////////////////////////////////////////////

async fn delete_ito(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(ito_id): Path<Id<Ito>>,
) -> Result<Content<Id<Ito>>, ServiceError> {
    state
        .delete_ito(ito_id, Arc::new(operator))
        .await
        .map(|ito_id| Content(ito_id, accept))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/itos/{ito_id}/order
/////////////////////////////////////////////////////////////////////////////

async fn change_order(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(ito_id): Path<Id<Ito>>,
    Json(new_order): Json<i32>,
) -> Result<Content<Ito>, ServiceError> {
    state
        .change_ito_order(ito_id, new_order, Arc::new(operator))
        .await
        .map(|ito| Content(ito, accept))
}
