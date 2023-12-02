use axum::{
    extract::{
        ws::{WebSocket, WebSocketUpgrade},
        State,
    },
    middleware,
    response::IntoResponse,
    routing::get,
    Extension, Router,
};
use cotoami_db::prelude::*;

use crate::state::NodeState;

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(ws_handler))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/ws
/////////////////////////////////////////////////////////////////////////////

async fn ws_handler(
    ws: WebSocketUpgrade,
    State(state): State<NodeState>,
    Extension(session): Extension<ClientSession>,
) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle_socket(socket, state, session))
}

async fn handle_socket(mut socket: WebSocket, state: NodeState, session: ClientSession) {
    unimplemented!()
}
