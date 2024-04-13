use anyhow::Result;
use axum::{
    extract::{Path, State},
    routing::get,
    Router, TypedHeader,
};
use cotoami_db::prelude::*;

use crate::{
    service::{models::CotoGraph, ServiceError},
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> { Router::new().route("/", get(get_graph)) }

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotonomas/:cotonoma_id/graph
/////////////////////////////////////////////////////////////////////////////

async fn get_graph(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
) -> Result<Content<CotoGraph>, ServiceError> {
    state
        .coto_graph(cotonoma_id)
        .await
        .map(|graph| Content(graph, accept))
}
