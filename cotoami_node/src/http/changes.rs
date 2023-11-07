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
    api::error::{ApiError, IntoApiResult},
    AppState,
};

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", get(chunk_of_changes))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/changes
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize, serde::Deserialize, Validate)]
pub(crate) struct Position {
    #[validate(required, range(min = 1))]
    pub from: Option<i64>,
}

#[derive(serde::Serialize, serde::Deserialize)]
pub(crate) enum ChangesResult {
    Fetched(Changes),
    OutOfRange { max: i64 },
}

#[derive(serde::Serialize, serde::Deserialize)]
pub(crate) struct Changes {
    pub chunk: Vec<ChangelogEntry>,
    pub last_serial_number: i64,
}

impl Changes {
    pub fn last_serial_number_of_chunk(&self) -> i64 {
        self.chunk.last().map(|c| c.serial_number).unwrap_or(0)
    }

    pub fn is_last_chunk(&self) -> bool {
        if let Some(change) = self.chunk.last() {
            // For safety's sake (to avoid infinite loop), leave it as the last chunk
            // if the last serial number of it is equal **or larger than** the
            // last serial number of all, rather than exactly the same number.
            change.serial_number >= self.last_serial_number
        } else {
            true // empty (no changes) means the last chunk
        }
    }
}

async fn chunk_of_changes(
    State(state): State<AppState>,
    Query(position): Query<Position>,
) -> Result<Json<ChangesResult>, ApiError> {
    if let Err(errors) = position.validate() {
        return ("changes", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.new_session()?;
        match db.chunk_of_changes(position.from.unwrap(), state.config.changes_chunk_size) {
            Ok((chunk, last_serial_number)) => {
                let changes = Changes {
                    chunk,
                    last_serial_number,
                };
                Ok(Json(ChangesResult::Fetched(changes)))
            }
            Err(anyhow_err) => {
                if let Some(DatabaseError::ChangeNumberOutOfRange { max, .. }) =
                    anyhow_err.downcast_ref::<DatabaseError>()
                {
                    Ok(Json(ChangesResult::OutOfRange { max: *max }))
                } else {
                    Err(anyhow_err.into())
                }
            }
        }
    })
    .await?
}
