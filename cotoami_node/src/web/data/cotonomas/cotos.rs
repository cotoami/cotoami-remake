use std::sync::Arc;

use anyhow::Result;
use axum::{
    extract::{Json, Path, Query, State},
    http::StatusCode,
    routing::{get, post},
    Extension, Router, TypedHeader,
};
use cotoami_db::prelude::*;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{CotosPage, GeolocatedCotos, Pagination},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotos).post(post_coto))
        .route("/cotonomas", get(recent_cotonoma_cotos))
        .route("/repost", post(repost))
        .route("/geolocated", get(geolocated_cotos))
        .route("/search/:query", get(search_cotos))
        .route("/search/cotonomas/:query", get(search_cotonoma_cotos))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<CotosPage>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return errors.into_result();
    }
    state
        .recent_cotos(None, Some(cotonoma_id), false, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/cotos/cotonomas
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotonoma_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<CotosPage>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return errors.into_result();
    }
    state
        .recent_cotos(None, Some(cotonoma_id), true, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/data/cotonomas/:cotonoma_id/cotos
/////////////////////////////////////////////////////////////////////////////

async fn post_coto(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Json(input): Json<CotoInput<'static>>,
) -> Result<(StatusCode, Content<Coto>), ServiceError> {
    state
        .post_coto(input, cotonoma_id, Arc::new(operator))
        .await
        .map(|coto| (StatusCode::CREATED, Content(coto, accept)))
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/data/cotonomas/:cotonoma_id/cotos/repost
/////////////////////////////////////////////////////////////////////////////

async fn repost(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Json(coto_id): Json<Id<Coto>>,
) -> Result<(StatusCode, Content<(Coto, Coto)>), ServiceError> {
    state
        .repost(coto_id, cotonoma_id, Arc::new(operator))
        .await
        .map(|repost| (StatusCode::CREATED, Content(repost, accept)))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/cotos/geolocated
/////////////////////////////////////////////////////////////////////////////

async fn geolocated_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
) -> Result<Content<GeolocatedCotos>, ServiceError> {
    state
        .geolocated_cotos(None, Some(cotonoma_id))
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/cotos/search/:query
/////////////////////////////////////////////////////////////////////////////

async fn search_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path((cotonoma_id, query)): Path<(Id<Cotonoma>, String)>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<CotosPage>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return errors.into_result();
    }
    state
        .search_cotos(query, None, Some(cotonoma_id), false, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/cotos/cotonomas/search/:query
/////////////////////////////////////////////////////////////////////////////

async fn search_cotonoma_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path((cotonoma_id, query)): Path<(Id<Cotonoma>, String)>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<CotosPage>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return errors.into_result();
    }
    state
        .search_cotos(query, None, Some(cotonoma_id), true, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}
