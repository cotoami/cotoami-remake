use anyhow::Result;
use axum::{
    extract::{Query, State},
    middleware,
    routing::get,
    Extension, Json, Router,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    service::{error::IntoServiceResult, models::Pagination, ServiceError},
    NodeState,
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotos))
        .layer(middleware::from_fn(super::require_operator))
        .layer(middleware::from_fn(super::require_session))
}

const DEFAULT_PAGE_SIZE: i64 = 30;

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<NodeState>,
    Extension(_operator): Extension<Operator>,
    Query(pagination): Query<Pagination>,
) -> Result<Json<Paginated<Coto>>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("cotos", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db().new_session()?;
        let cotos = db.recent_cotos(
            None,
            None,
            pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
            pagination.page,
        )?;
        Ok(Json(cotos))
    })
    .await?
}
