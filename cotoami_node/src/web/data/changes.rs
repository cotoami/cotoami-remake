use axum::{
    extract::{Query, State},
    routing::get,
    Router,
};
use axum_extra::TypedHeader;
use validator::Validate;

use crate::{
    service::{error::IntoServiceResult, models::ChunkOfChanges, ServiceError},
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> { Router::new().route("/", get(chunk_of_changes)) }

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/changes
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
        return errors.into_result();
    }
    let from = position.from.unwrap_or_else(|| unreachable!());
    state
        .chunk_of_changes(from)
        .await
        .map(|changes| Content(changes, accept))
}
