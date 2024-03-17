use axum::{
    extract::{Query, State},
    middleware,
    routing::get,
    Router, TypedHeader,
};
use validator::Validate;

use crate::{
    service::{error::IntoServiceResult, models::ChunkOfChanges, ServiceError},
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(chunk_of_changes))
        .layer(middleware::from_fn(super::require_operator))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/changes
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize, serde::Deserialize, Validate)]
struct Position {
    #[validate(required, range(min = 1))]
    pub from: Option<i64>,
}

async fn chunk_of_changes(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(position): Query<Position>,
) -> Result<Content<ChunkOfChanges>, ServiceError> {
    if let Err(errors) = position.validate() {
        return ("changes", errors).into_result();
    }
    let from = position.from.unwrap_or_else(|| unreachable!());
    state
        .chunk_of_changes(from)
        .await
        .map(|x| Content(x, accept))
}
