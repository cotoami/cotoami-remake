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
use futures::StreamExt;
use tracing::error;

use crate::{event::NodeSentEvent, state::NodeState};

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
    if let ClientSession::ParentNode(parent) = session {
        // For parent-client
        unimplemented!();
    } else {
        // For child-client

        // Publish change events
        let mut changes = state.pubsub().local_changes().subscribe(None::<()>);
        tokio::spawn(async move {
            while let Some(change) = changes.next().await {
                send_event(&mut socket, NodeSentEvent::Change(change), |_| {}).await;
            }
        });

        // Accept request events
    }
}

async fn send_event<F>(socket: &mut WebSocket, event: NodeSentEvent, on_disconnected: F)
where
    F: Fn(axum::Error),
{
    match rmp_serde::to_vec(&event).map(Bytes::from) {
        Ok(bytes) => {
            if let Err(e) = socket.send(Message::Binary(bytes.into())).await {
                on_disconnected(e);
            }
        }
        Err(e) => error!("Event serialization error: {}", e),
    }
}
