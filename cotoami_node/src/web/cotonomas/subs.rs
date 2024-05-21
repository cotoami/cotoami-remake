use std::sync::Arc;

use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    routing::get,
    Extension, Form, Router, TypedHeader,
};
use cotoami_db::prelude::*;

use crate::{
    service::{
        models::{CotonomaInput, Pagination},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new().route("/", get(sub_cotonomas).post(post_cotonoma))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotonomas/:cotonoma_id/subs
/////////////////////////////////////////////////////////////////////////////

async fn sub_cotonomas(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<Paginated<Cotonoma>>, ServiceError> {
    state
        .sub_cotonomas(cotonoma_id, pagination)
        .await
        .map(|cotonomas| Content(cotonomas, accept))
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/cotonomas/:cotonoma_id/subs
/////////////////////////////////////////////////////////////////////////////

async fn post_cotonoma(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Form(form): Form<CotonomaInput>,
) -> Result<(StatusCode, Content<(Cotonoma, Coto)>), ServiceError> {
    state
        .post_cotonoma(form, cotonoma_id, Arc::new(operator))
        .await
        .map(|cotonoma| (StatusCode::CREATED, Content(cotonoma, accept)))
}
