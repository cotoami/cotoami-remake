use std::sync::Arc;

use anyhow::Result;
use axum::{
    extract::{Json, Path, Query, State},
    http::StatusCode,
    routing::{get, post, put},
    Extension, Router,
};
use axum_extra::TypedHeader;
use cotoami_db::prelude::*;

use crate::{
    service::{
        models::{CotoDetails, CotoGraph, GeolocatedCotos, PaginatedCotos, Pagination},
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
        .route(
            "/geo/{sw_lng}/{sw_lat}/{ne_lng}/{ne_lat}",
            get(cotos_in_geo_bounds),
        )
        .route("/search/{query}", get(search_cotos))
        .route("/search/cotonomas/{query}", get(search_cotonoma_cotos))
        .route("/{coto_id}/details", get(coto_details))
        .route("/{coto_id}/cotonoma", get(cotonoma))
        .route("/{coto_id}", put(edit_coto).delete(delete_coto))
        .route("/{coto_id}/promote", put(promote))
        .route("/{coto_id}/itos", get(sibling_itos))
        .route("/{coto_id}/graph", get(graph))
        .route("/{coto_id}/subcotos", post(post_subcoto))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    state
        .recent_cotos(None, None, false, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotos/cotonomas
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotonoma_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    state
        .recent_cotos(None, None, true, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotos/geolocated
/////////////////////////////////////////////////////////////////////////////

async fn geolocated_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
) -> Result<Content<GeolocatedCotos>, ServiceError> {
    state
        .geolocated_cotos(None, None)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotos/geo/{sw_lng}/{sw_lat}/{ne_lng}/{ne_lat}
/////////////////////////////////////////////////////////////////////////////

async fn cotos_in_geo_bounds(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path((sw_lng, sw_lat, ne_lng, ne_lat)): Path<(f64, f64, f64, f64)>,
) -> Result<Content<GeolocatedCotos>, ServiceError> {
    state
        .cotos_in_geo_bounds(
            Geolocation::from_lng_lat((sw_lng, sw_lat)),
            Geolocation::from_lng_lat((ne_lng, ne_lat)),
        )
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotos/search/{query}
/////////////////////////////////////////////////////////////////////////////

async fn search_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(query): Path<String>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    state
        .search_cotos(query, None, None, false, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotos/cotonomas/search/{query}
/////////////////////////////////////////////////////////////////////////////

async fn search_cotonoma_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(query): Path<String>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    state
        .search_cotos(query, None, None, true, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotos/{coto_id}/details
/////////////////////////////////////////////////////////////////////////////

async fn coto_details(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(coto_id): Path<Id<Coto>>,
) -> Result<Content<CotoDetails>, ServiceError> {
    state
        .coto_details(coto_id)
        .await
        .map(|details| Content(details, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotos/{coto_id}/cotonoma
/////////////////////////////////////////////////////////////////////////////

async fn cotonoma(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(coto_id): Path<Id<Coto>>,
) -> Result<Content<(Cotonoma, Coto)>, ServiceError> {
    state
        .cotonoma_pair_by_coto_id(coto_id)
        .await
        .map(|cotonoma| Content(cotonoma, accept))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/cotos/{coto_id}
/////////////////////////////////////////////////////////////////////////////

async fn edit_coto(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(coto_id): Path<Id<Coto>>,
    Json(diff): Json<CotoContentDiff<'static>>,
) -> Result<Content<Coto>, ServiceError> {
    state
        .edit_coto(coto_id, diff, Arc::new(operator))
        .await
        .map(|coto| Content(coto, accept))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/cotos/{coto_id}/promote
/////////////////////////////////////////////////////////////////////////////

async fn promote(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(coto_id): Path<Id<Coto>>,
) -> Result<Content<(Cotonoma, Coto)>, ServiceError> {
    state
        .promote(coto_id, Arc::new(operator))
        .await
        .map(|cotonoma| Content(cotonoma, accept))
}

/////////////////////////////////////////////////////////////////////////////
// DELETE /api/data/cotos/{coto_id}
/////////////////////////////////////////////////////////////////////////////

async fn delete_coto(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(coto_id): Path<Id<Coto>>,
) -> Result<Content<Id<Coto>>, ServiceError> {
    state
        .delete_coto(coto_id, Arc::new(operator))
        .await
        .map(|coto_id| Content(coto_id, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotos/{coto_id}/itos
/////////////////////////////////////////////////////////////////////////////

async fn sibling_itos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(coto_id): Path<Id<Coto>>,
    Query(filter): Query<SiblingItosFilter>,
) -> Result<Content<Vec<Ito>>, ServiceError> {
    state
        .sibling_itos(coto_id, filter.node)
        .await
        .map(|itos| Content(itos, accept))
}

#[derive(Debug, serde::Deserialize)]
pub struct SiblingItosFilter {
    #[serde(default)]
    pub node: Option<Id<Node>>,
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotos/{coto_id}/graph
/////////////////////////////////////////////////////////////////////////////

async fn graph(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(coto_id): Path<Id<Coto>>,
) -> Result<Content<CotoGraph>, ServiceError> {
    state
        .graph_from_coto(coto_id)
        .await
        .map(|graph| Content(graph, accept))
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/data/cotos/{coto_id}/subcotos?post_to=xxx
/////////////////////////////////////////////////////////////////////////////

async fn post_subcoto(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(source_coto_id): Path<Id<Coto>>,
    Query(destination): Query<Destination>,
    Json(input): Json<CotoInput<'static>>,
) -> Result<(StatusCode, Content<(Coto, Ito)>), ServiceError> {
    state
        .post_subcoto(
            source_coto_id,
            input,
            destination.post_to,
            Arc::new(operator),
        )
        .await
        .map(|subcoto| (StatusCode::CREATED, Content(subcoto, accept)))
}

#[derive(Debug, serde::Deserialize)]
pub struct Destination {
    #[serde(default)]
    pub post_to: Option<Id<Cotonoma>>,
}
