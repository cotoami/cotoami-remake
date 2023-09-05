use anyhow::Result;
use axum::{
    extract::{Query, State},
    middleware,
    routing::get,
    Json, Router,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    error::{ApiError, IntoApiResult},
    AppState,
};

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", get(sequence_of_changes))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/changes
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize, serde::Deserialize, Validate)]
pub(crate) struct Range {
    #[validate(required, range(min = 1))]
    pub from: Option<i64>,

    #[validate(required, range(min = 1))]
    pub limit: Option<i64>,
}

#[derive(serde::Serialize, serde::Deserialize)]
pub(crate) struct Changes {
    sequence: Vec<ChangelogEntry>,
    last_serial_number: i64,
}

async fn sequence_of_changes(
    State(state): State<AppState>,
    Query(range): Query<Range>,
) -> Result<Json<Changes>, ApiError> {
    if let Err(errors) = range.validate() {
        return ("changes", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        let changes = db.sequence_of_changes(range.from.unwrap(), range.limit.unwrap())?;
        let last_serial_number = db.last_serial_number_of_changes()?.unwrap_or(0);
        Ok(Json(Changes {
            sequence: changes,
            last_serial_number,
        }))
    })
    .await?
}
