use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        State,
    },
    middleware,
    response::IntoResponse,
    routing::get,
    Extension, Router,
};
use bytes::Bytes;
use cotoami_db::prelude::*;
use futures::SinkExt;
use futures_util::stream::SplitSink;

use crate::{event::NodeSentEvent, state::NodeState};

mod child;
mod parent;

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

async fn handle_socket(socket: WebSocket, state: NodeState, session: ClientSession) {
    match session {
        ClientSession::Operator(opr) => {
            child::handle_child(socket, state, opr).await;
        }
        ClientSession::ParentNode(parent) => {
            parent::handle_parent(socket, state, parent).await;
        }
    }
}

async fn send_event(
    socket_sink: &mut SplitSink<WebSocket, Message>,
    event: NodeSentEvent,
) -> Result<(), axum::Error> {
    let bytes = rmp_serde::to_vec(&event)
        .map(Bytes::from)
        .expect("A NodeSentEvent should be serializable into MessagePack");
    socket_sink.send(Message::Binary(bytes.into())).await
}
