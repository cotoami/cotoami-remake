use std::sync::Arc;

use anyhow::Result;
use axum::{
    extract::{Json, Path, State},
    routing::{delete, post},
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
        .route("/{link_id}", delete(disconnect))
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
