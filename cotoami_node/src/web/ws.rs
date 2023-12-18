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
    event::tungstenite::{handle_operator, handle_parent},
    state::NodeState,
};

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
    let (sink, stream) = socket.split();

    // Convert sink/stream to handle tungstenite messages
    let stream = stream.map(|r| r.map(into_tungstenite));
    let sink = Box::pin(sink.with(|m: ts::Message| async {
        from_tungstenite(m).ok_or(anyhow::anyhow!("Unexpected message."))
    }));

    match session {
        ClientSession::Operator(opr) => {
            handle_operator(opr, stream, sink, &state, &mut Vec::new()).await;
        }
        ClientSession::ParentNode(parent) => {
            handle_parent(
                parent.node_id,
                &format!("WebSocket client-as-parent: {}", parent.node_id),
                stream,
                sink,
                &state,
                &mut Vec::new(),
            )
            .await;
        }
    }
}

// This code comes from:
// https://github.com/tokio-rs/axum/blob/axum-v0.7.2/axum/src/extract/ws.rs#L591
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

// This code comes from:
// https://github.com/tokio-rs/axum/blob/axum-v0.7.2/axum/src/extract/ws.rs#L605
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
