use std::sync::Arc;

use axum::{
    extract::{
        ws::{CloseFrame, Message, WebSocket, WebSocketUpgrade},
        State,
    },
    middleware,
    response::IntoResponse,
    routing::get,
    Extension, Router,
};
use cotoami_db::prelude::*;
use futures::{SinkExt, StreamExt};
use tokio_tungstenite::tungstenite as ts;

use crate::{
    event::remote::tungstenite::{communicate_with_operator, communicate_with_parent},
    state::NodeState,
    Abortables,
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(ws_handler))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/ws
/////////////////////////////////////////////////////////////////////////////

/// The handler for the HTTP request (this gets called when the HTTP GET lands at the start
/// of websocket negotiation). After this completes, the actual switching from HTTP to
/// websocket protocol will occur.
async fn ws_handler(
    ws: WebSocketUpgrade,
    State(state): State<NodeState>,
    Extension(session): Extension<ClientSession>,
) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle_socket(socket, state, session))
}

/// Actual websocket statemachine (one will be spawned per connection)
async fn handle_socket(socket: WebSocket, state: NodeState, session: ClientSession) {
    let (sink, stream) = socket.split();

    // Adapt axum's sink/stream to handle tungstenite messages
    // cf. https://github.com/davidpdrsn/axum-tungstenite
    let sink = Box::pin(sink.with(|m: ts::Message| async {
        from_tungstenite(m).ok_or(anyhow::anyhow!("Unexpected message."))
    }));
    let stream = stream.map(|r| r.map(into_tungstenite));

    // The `communication_tasks` will be terminated when the connection is closed.
    let communication_tasks = Abortables::new();
    match session {
        ClientSession::Operator(opr) => {
            communicate_with_operator(
                state,
                Arc::new(opr),
                sink,
                stream,
                futures::sink::drain(),
                communication_tasks,
            )
            .await;
        }
        ClientSession::ParentNode(parent) => {
            communicate_with_parent(
                state,
                parent.node_id,
                format!("WebSocket client-as-parent: {}", parent.node_id),
                sink,
                stream,
                futures::sink::drain(),
                communication_tasks,
            )
            .await;
        }
    }
}

/// Convert an axum's [Message] into a tungstenite's [ts::Message].
///
/// This code comes from:
/// <https://github.com/tokio-rs/axum/blob/axum-v0.7.2/axum/src/extract/ws.rs#L591>
fn into_tungstenite(msg: Message) -> ts::Message {
    match msg {
        Message::Text(text) => ts::Message::Text(text),
        Message::Binary(binary) => ts::Message::Binary(binary),
        Message::Ping(ping) => ts::Message::Ping(ping),
        Message::Pong(pong) => ts::Message::Pong(pong),
        Message::Close(Some(close)) => ts::Message::Close(Some(ts::protocol::CloseFrame {
            code: ts::protocol::frame::coding::CloseCode::from(close.code),
            reason: close.reason,
        })),
        Message::Close(None) => ts::Message::Close(None),
    }
}

/// Convert a tungstenite's [ts::Message] into an axum's [Message].
///
/// This code comes from:
/// <https://github.com/tokio-rs/axum/blob/axum-v0.7.2/axum/src/extract/ws.rs#L605>
fn from_tungstenite(message: ts::Message) -> Option<Message> {
    match message {
        ts::Message::Text(text) => Some(Message::Text(text)),
        ts::Message::Binary(binary) => Some(Message::Binary(binary)),
        ts::Message::Ping(ping) => Some(Message::Ping(ping)),
        ts::Message::Pong(pong) => Some(Message::Pong(pong)),
        ts::Message::Close(Some(close)) => Some(Message::Close(Some(CloseFrame {
            code: close.code.into(),
            reason: close.reason,
        }))),
        ts::Message::Close(None) => Some(Message::Close(None)),
        // we can ignore `Frame` frames as recommended by the tungstenite maintainers
        // https://github.com/snapview/tungstenite-rs/issues/268
        ts::Message::Frame(_) => None,
    }
}
