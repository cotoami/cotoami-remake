use axum::{extract::State, middleware, routing::get, Json, Router};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{api::error::ApiError, AppState};

mod children;
mod parents;

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/local", get(get_local_node))
        .nest("/parents", parents::routes())
        .nest("/children", children::routes())
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/local
/////////////////////////////////////////////////////////////////////////////

async fn get_local_node(State(state): State<AppState>) -> Result<Json<Node>, ApiError> {
    spawn_blocking(move || {
        let mut db = state.db.new_session()?;
        Ok(Json(db.local_node()?))
    })
    .await?
}
