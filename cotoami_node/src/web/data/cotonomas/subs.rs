use std::sync::Arc;

use anyhow::Result;
use axum::{
    extract::{Json, Path, Query, State},
    http::StatusCode,
    routing::get,
    Extension, Router, TypedHeader,
};
use cotoami_db::prelude::*;

use crate::{
    service::{models::Pagination, ServiceError},
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new().route("/", get(sub_cotonomas).post(post_cotonoma))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/subs
/////////////////////////////////////////////////////////////////////////////

async fn sub_cotonomas(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<Page<Cotonoma>>, ServiceError> {
    state
        .sub_cotonomas(cotonoma_id, pagination)
        .await
        .map(|cotonomas| Content(cotonomas, accept))
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/data/cotonomas/:cotonoma_id/subs
/////////////////////////////////////////////////////////////////////////////

async fn post_cotonoma(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Json(input): Json<CotonomaInput<'static>>,
) -> Result<(StatusCode, Content<(Cotonoma, Coto)>), ServiceError> {
    state
        .post_cotonoma(input, cotonoma_id, Arc::new(operator))
        .await
        .map(|cotonoma| (StatusCode::CREATED, Content(cotonoma, accept)))
}
