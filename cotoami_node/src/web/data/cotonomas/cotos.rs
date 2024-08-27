use std::sync::Arc;

use anyhow::Result;
use axum::{
    extract::{Json, Path, Query, State},
    http::StatusCode,
    routing::get,
    Extension, Router, TypedHeader,
};
use cotoami_db::prelude::*;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{PaginatedCotos, Pagination},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotos).post(post_coto))
        .route("/search/:query", get(search_cotos))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("cotos", errors).into_result();
    }
    state
        .recent_cotos(None, Some(cotonoma_id), pagination)
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
// GET /api/data/cotonomas/:cotonoma_id/cotos/search/:query
/////////////////////////////////////////////////////////////////////////////

async fn search_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path((cotonoma_id, query)): Path<(Id<Cotonoma>, String)>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("cotos", errors).into_result();
    }
    state
        .search_cotos(query, None, Some(cotonoma_id), pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}
