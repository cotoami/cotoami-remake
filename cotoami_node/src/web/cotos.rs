use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    middleware,
    routing::get,
    Router, TypedHeader,
};
use cotoami_db::prelude::*;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{CotoGraph, PaginatedCotos, Pagination},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotos))
        .route("/search/:query", get(search_cotos))
        .route("/:coto_id/graph", get(get_graph))
        .layer(middleware::from_fn(super::require_operator))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("cotos", errors).into_result();
    }
    state
        .recent_cotos(None, None, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotos/search/:query
/////////////////////////////////////////////////////////////////////////////

async fn search_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(query): Path<String>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("cotos", errors).into_result();
    }
    state
        .search_cotos(query, None, None, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotos/:coto_id/graph
/////////////////////////////////////////////////////////////////////////////

async fn get_graph(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(coto_id): Path<Id<Coto>>,
) -> Result<Content<CotoGraph>, ServiceError> {
    state
        .graph_from_coto(coto_id)
        .await
        .map(|graph| Content(graph, accept))
}
