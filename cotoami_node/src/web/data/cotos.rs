use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    routing::get,
    Router, TypedHeader,
};
use cotoami_db::prelude::*;

use crate::{
    service::{
        models::{CotoGraph, CotosPage, GeolocatedCotos, Pagination},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotos))
        .route("/geolocated", get(geolocated_cotos))
        .route(
            "/geo/:sw_lng/:sw_lat/:ne_lng/:ne_lat",
            get(cotos_in_geo_bounds),
        )
        .route("/search/:query", get(search_cotos))
        .route("/:coto_id/graph", get(get_graph))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<CotosPage>, ServiceError> {
    state
        .recent_cotos(None, None, pagination)
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
// GET /api/data/cotos/geo/:sw_lng/:sw_lat/:ne_lng/:ne_lat
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
// GET /api/data/cotos/search/:query
/////////////////////////////////////////////////////////////////////////////

async fn search_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(query): Path<String>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<CotosPage>, ServiceError> {
    state
        .search_cotos(query, None, None, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotos/:coto_id/graph
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
