use std::{net::SocketAddr, sync::Arc};

use axum::{
    extract::{
        ws::{CloseFrame, Message, WebSocket, WebSocketUpgrade},
        ConnectInfo, State,
    },
    middleware,
    response::IntoResponse,
    routing::get,
    Extension, Router,
};
use cotoami_db::prelude::*;
use futures::{sink::Sink, SinkExt, StreamExt};
use tokio::sync::oneshot;
use tokio_tungstenite::tungstenite as ts;
use tracing::debug;

use crate::{
    event::remote::{
        tungstenite::{communicate_with_operator, communicate_with_parent},
        EventLoopError,
    },
    state::{ClientConnection, NodeState},
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
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    State(state): State<NodeState>,
    Extension(session): Extension<ClientSession>,
) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle_socket(socket, addr, state, session))
}

/// Actual websocket statemachine (one will be spawned per connection)
async fn handle_socket(
    socket: WebSocket,
    remote_addr: SocketAddr,
    state: NodeState,
    session: ClientSession,
) {
    // Publish connect and disconnect
    let client_id = session.client_node_id();
    let (disconnect, disconnect_receiver) = oneshot::channel::<()>();
    state.put_client_conn(ClientConnection::new(
        client_id,
        remote_addr.ip().to_string(),
        disconnect,
    ));
    let on_disconnect = listener_on_disconnect(client_id, state.clone());
    futures::pin_mut!(on_disconnect);

    let (sink, stream) = socket.split();

    // Adapt axum's sink/stream to handle tungstenite messages
    // cf. https://github.com/davidpdrsn/axum-tungstenite
    let sink = Box::pin(sink.with(|m: ts::Message| async {
        from_tungstenite(m).ok_or(anyhow::anyhow!("Unexpected message."))
    }));
    let stream = stream.map(|r| r.map(into_tungstenite));

    let communication_tasks = Abortables::default();
    tokio::spawn({
        // Close the communication when a disconnect message has been received.
        let tasks = communication_tasks.clone();
        async move {
            match disconnect_receiver.await {
                Ok(_) => {
                    debug!("Disconnecting a client {client_id} ...");
                    tasks.abort_all()
                }
                Err(_) => (), // the sender dropped
            }
        }
    });

    match session {
        ClientSession::Operator(opr) => {
            communicate_with_operator(
                state,
                Arc::new(opr),
                sink,
                stream,
                on_disconnect,
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
                on_disconnect,
                communication_tasks,
            )
            .await;
        }
    }
}

fn listener_on_disconnect(
    client_id: Id<Node>,
    state: NodeState,
) -> impl Sink<Option<EventLoopError>, Error = futures::never::Never> + 'static {
    futures::sink::unfold((), move |(), error: Option<EventLoopError>| {
        let state = state.clone();
        async move {
            state.remove_client_conn(&client_id, error.map(|e| e.to_string()));
            Ok(())
        }
    })
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
