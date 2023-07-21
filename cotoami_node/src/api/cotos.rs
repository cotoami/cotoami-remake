use crate::api::{ClientErrors, Pagination, WebError};
use crate::AppState;
use axum::extract::{Query, State};
use axum::routing::get;
use axum::{Json, Router};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

pub(super) fn routes() -> Router<AppState> {
    Router::new().route("/", get(recent_cotos))
}

const DEFAULT_PAGE_SIZE: i64 = 30;

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<AppState>,
    Query(pagination): Query<Pagination>,
) -> Result<Json<Paginated<Coto>>, WebError> {
    if let Err(errors) = pagination.validate() {
        return ClientErrors::from_validation_errors("cotos", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
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
