use axum::{extract::State, middleware, response::IntoResponse, routing::get, Extension, Router};
use cotoami_db::prelude::*;

use crate::state::NodeState;

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(ws_handler))
        .layer(middleware::from_fn(super::require_operator))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/ws
/////////////////////////////////////////////////////////////////////////////

async fn ws_handler(
    State(_state): State<NodeState>,
    Extension(_operator): Extension<Operator>,
) -> impl IntoResponse {
    unimplemented!();
}
