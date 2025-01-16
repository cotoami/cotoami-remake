use axum::{
    extract::{Path, Query, State},
    routing::get,
    Router,
};
use axum_extra::TypedHeader;
use cotoami_db::prelude::*;

use crate::{
    service::{
        models::{GeolocatedCotos, PaginatedCotos, Pagination},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotos))
        .route("/cotonomas", get(recent_cotonoma_cotos))
        .route("/geolocated", get(geolocated_cotos))
        .route("/search/:query", get(search_cotos))
        .route("/search/cotonomas/:query", get(search_cotonoma_cotos))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/:node_id/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(node_id): Path<Id<Node>>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    state
        .recent_cotos(Some(node_id), None, false, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/:node_id/cotos/cotonomas
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotonoma_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(node_id): Path<Id<Node>>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    state
        .recent_cotos(Some(node_id), None, true, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/:node_id/cotos/geolocated
/////////////////////////////////////////////////////////////////////////////

async fn geolocated_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(node_id): Path<Id<Node>>,
) -> Result<Content<GeolocatedCotos>, ServiceError> {
    state
        .geolocated_cotos(Some(node_id), None)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/:node_id/cotos/search/:query
/////////////////////////////////////////////////////////////////////////////

async fn search_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path((node_id, query)): Path<(Id<Node>, String)>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    state
        .search_cotos(query, Some(node_id), None, false, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/:node_id/cotos/cotonomas/search/:query
/////////////////////////////////////////////////////////////////////////////

async fn search_cotonoma_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path((node_id, query)): Path<(Id<Node>, String)>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    state
        .search_cotos(query, Some(node_id), None, true, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}
