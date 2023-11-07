use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    middleware,
    routing::get,
    Json, Router,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    error::{ApiError, IntoApiResult},
    http::Pagination,
    AppState,
};

mod cotos;

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", get(recent_cotonomas))
        .route("/:cotonoma_id", get(get_cotonoma))
        .nest("/:cotonoma_id/cotos", cotos::routes())
        .layer(middleware::from_fn(super::require_session))
}

const DEFAULT_PAGE_SIZE: i64 = 100;

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotonomas
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotonomas(
    State(state): State<AppState>,
    Query(pagination): Query<Pagination>,
) -> Result<Json<Paginated<Cotonoma>>, ApiError> {
    if let Err(errors) = pagination.validate() {
        return ("cotonomas", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.new_session()?;
        let cotonomas = db.recent_cotonomas(
            None,
            pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
            pagination.page,
        )?;
        Ok(Json(cotonomas))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotonomas/:cotonoma_id
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct CotonomaDetails {
    cotonoma: Cotonoma,
    coto: Coto,
}

async fn get_cotonoma(
    State(state): State<AppState>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
) -> Result<Json<CotonomaDetails>, ApiError> {
    spawn_blocking(move || {
        let mut db = state.db.new_session()?;
        let (cotonoma, coto) = db.cotonoma_or_err(&cotonoma_id)?;
        Ok(Json(CotonomaDetails { cotonoma, coto }))
    })
    .await?
}
