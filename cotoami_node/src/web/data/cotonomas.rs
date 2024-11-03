use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    routing::get,
    Router, TypedHeader,
};
use cotoami_db::prelude::*;

use crate::{
    service::{
        models::{CotoGraph, CotonomaDetails, Pagination},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

mod cotos;
mod subs;

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(get_recent_cotonomas))
        .route("/prefix/:prefix", get(get_cotonomas_by_prefix))
        .route("/:cotonoma_id", get(get_cotonoma))
        .route("/:cotonoma_id/details", get(get_cotonoma_details))
        .route("/:cotonoma_id/graph", get(get_graph))
        .nest("/:cotonoma_id/subs", subs::routes())
        .nest("/:cotonoma_id/cotos", cotos::routes())
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas
/////////////////////////////////////////////////////////////////////////////

async fn get_recent_cotonomas(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<Page<Cotonoma>>, ServiceError> {
    state
        .recent_cotonomas(None, pagination)
        .await
        .map(|cotonomas| Content(cotonomas, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/prefix/:prefix
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, serde::Deserialize)]
pub struct TargetNodesQuery {
    node: Option<Vec<Id<Node>>>,
}

async fn get_cotonomas_by_prefix(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(prefix): Path<String>,
    Query(target_nodes): Query<TargetNodesQuery>,
) -> Result<Content<Vec<(Cotonoma, Coto)>>, ServiceError> {
    state
        .cotonomas_by_prefix(prefix, target_nodes.node)
        .await
        .map(|cotonomas| Content(cotonomas, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id
/////////////////////////////////////////////////////////////////////////////

async fn get_cotonoma(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
) -> Result<Content<(Cotonoma, Coto)>, ServiceError> {
    state
        .cotonoma(cotonoma_id)
        .await
        .map(|cotonoma| Content(cotonoma, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/details
/////////////////////////////////////////////////////////////////////////////

async fn get_cotonoma_details(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
) -> Result<Content<CotonomaDetails>, ServiceError> {
    state
        .cotonoma_details(cotonoma_id)
        .await
        .map(|details| Content(details, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/graph
/////////////////////////////////////////////////////////////////////////////

async fn get_graph(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
) -> Result<Content<CotoGraph>, ServiceError> {
    state
        .graph_from_cotonoma(cotonoma_id)
        .await
        .map(|graph| Content(graph, accept))
}
