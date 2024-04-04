use anyhow::Result;
use axum::{
    extract::{Query, State},
    middleware,
    routing::get,
    Router, TypedHeader,
};
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{Cotos, Pagination},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotos))
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
) -> Result<Content<Cotos>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("cotos", errors).into_result();
    }
    state
        .recent_cotos(None, None, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}
