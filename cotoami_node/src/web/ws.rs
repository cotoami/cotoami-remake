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
use futures::{sink::Sink, stream::Stream, FutureExt, SinkExt, StreamExt};
use tokio::sync::oneshot;
use tokio_tungstenite::tungstenite as ts;
use tracing::debug;
use uuid::Uuid;

use crate::{
    event::remote::{
        tungstenite::{communicate_with_operator, communicate_with_parent},
        CommunicationError,
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
    mut ws: WebSocketUpgrade,
    ConnectInfo(addr): ConnectInfo<SocketAddr>,
    State(state): State<NodeState>,
    Extension(session): Extension<ClientSession>,
) -> impl IntoResponse {
    ws = if let Some(max_message_size) = state.read_config().max_message_size_as_server {
        ws.max_message_size(max_message_size)
            .max_frame_size(max_message_size)
    } else {
        ws
    };

    ws.on_upgrade(move |socket| match session.client_node_id() {
        Some(client_id) => handle_authenticated(socket, addr, state, session, client_id).boxed(),
        None => handle_anonymous(socket, addr, state).boxed(),
    })
}

/// Split a [WebSocket] into a [Sink] and [Stream] that handle [ts::Message]s.
fn split_socket(
    socket: WebSocket,
) -> (
    impl Sink<ts::Message, Error = anyhow::Error>,
    impl Stream<Item = Result<ts::Message, axum::Error>>,
) {
    let (sink, stream) = socket.split();

    // Adapt axum's sink/stream to handle tungstenite messages.
    // cf. https://github.com/davidpdrsn/axum-tungstenite
    let sink = Box::pin(sink.with(|m: ts::Message| async {
        from_tungstenite(m).ok_or(anyhow::anyhow!("Unexpected message."))
    }));
    let stream = stream.map(|r| r.map(into_tungstenite));

    (sink, stream)
}

/////////////////////////////////////////////////////////////////////////////
// Handle authenticated client
/////////////////////////////////////////////////////////////////////////////

async fn handle_authenticated(
    socket: WebSocket,
    remote_addr: SocketAddr,
    state: NodeState,
    session: ClientSession,
    client_id: Id<Node>,
) {
    let (sink, stream) = split_socket(socket);

    // Container of tasks to maintain this client-server connection.
    let communication_tasks = Abortables::default();

    // A task receiving a manual disconnect message.
    let (tx_disconnect, rx_disconnect) = oneshot::channel::<()>();
    tokio::spawn({
        let state = state.clone();
        let tasks = communication_tasks.clone();
        async move {
            match rx_disconnect.await {
                Ok(_) => {
                    debug!("Disconnecting a client {client_id} ...");
                    state.clear_client_node_session(client_id).await.unwrap();
                    tasks.abort_all();
                }
                Err(_) => (), // the sender dropped
            }
        }
    });

    // Register a ClientConnection.
    state.put_client_conn(ClientConnection::new(
        client_id,
        remote_addr.ip().to_string(),
        tx_disconnect,
    ));

    // Event handler on disconnect
    let on_disconnect = on_client_disconnect(client_id, state.clone());
    futures::pin_mut!(on_disconnect);

    match session {
        ClientSession::Operator(opr) => {
            communicate_with_operator(
                state,
                Arc::new(opr),
                sink,
                stream,
                on_disconnect,
                None, // No pings
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
                None, // No pings
                communication_tasks,
            )
            .await;
        }
    }
}

fn on_client_disconnect(
    client_id: Id<Node>,
    state: NodeState,
) -> impl Sink<Option<CommunicationError>, Error = futures::never::Never> + 'static {
    futures::sink::unfold((), move |(), error: Option<CommunicationError>| {
        let state = state.clone();
        async move {
            state.on_client_disconnect(client_id, error.map(|e| e.to_string()));
            Ok(())
        }
    })
}

/////////////////////////////////////////////////////////////////////////////
// Handle anonymous client
/////////////////////////////////////////////////////////////////////////////

async fn handle_anonymous(socket: WebSocket, remote_addr: SocketAddr, state: NodeState) {
    let (sink, stream) = split_socket(socket);

    // Container of tasks to maintain this client-server connection.
    let communication_tasks = Abortables::default();

    // A task receiving a manual disconnect message.
    let (tx_disconnect, rx_disconnect) = oneshot::channel::<()>();
    tokio::spawn({
        let tasks = communication_tasks.clone();
        async move {
            match rx_disconnect.await {
                Ok(_) => {
                    debug!("Disconnecting an anonymous client...");
                    tasks.abort_all();
                }
                Err(_) => (), // the sender dropped
            }
        }
    });
    let conn_id = state.add_anonymous_conn(remote_addr.ip().to_string(), tx_disconnect);

    // Event handler on disconnect
    let on_disconnect = on_anonymous_disconnect(conn_id, state.clone());
    futures::pin_mut!(on_disconnect);

    communicate_with_operator(
        state,
        Arc::new(Operator::Anonymous),
        sink,
        stream,
        on_disconnect,
        None, // No pings
        communication_tasks,
    )
    .await;
}

fn on_anonymous_disconnect(
    id: Uuid,
    state: NodeState,
) -> impl Sink<Option<CommunicationError>, Error = futures::never::Never> + 'static {
    futures::sink::unfold((), move |(), _error: Option<CommunicationError>| {
        let state = state.clone();
        async move {
            state.remove_anonymous_conn(&id);
            Ok(())
        }
    })
}

// Convert WebSocket messages between [axum::extract::ws::Message] and
// [tungstenite::protocol::Message] to use [event::remote::tungstenite] which is
// used from both client and server.
//
// cf. Axum's WebSocket Message is copied from tungstenite.
//     https://docs.rs/axum/0.8.1/src/axum/extract/ws.rs.html#685

/// Convert an axum's [Message] into a tungstenite's [ts::Message].
///
/// This code comes from:
/// <https://github.com/tokio-rs/axum/blob/axum-v0.7.2/axum/src/extract/ws.rs#L591>
fn into_tungstenite(msg: Message) -> ts::Message {
    match msg {
        Message::Text(text) => ts::Message::Text(to_ts_utf8_bytes(text)),
        Message::Binary(binary) => ts::Message::Binary(binary),
        Message::Ping(ping) => ts::Message::Ping(ping),
        Message::Pong(pong) => ts::Message::Pong(pong),
        Message::Close(Some(close)) => ts::Message::Close(Some(ts::protocol::CloseFrame {
            code: ts::protocol::frame::coding::CloseCode::from(close.code),
            reason: to_ts_utf8_bytes(close.reason),
        })),
        Message::Close(None) => ts::Message::Close(None),
    }
}

fn to_ts_utf8_bytes(bytes: axum::extract::ws::Utf8Bytes) -> ts::protocol::frame::Utf8Bytes {
    ts::protocol::frame::Utf8Bytes::from(bytes.as_str())
}

/// Convert a tungstenite's [ts::Message] into an axum's [Message].
///
/// This code comes from:
/// <https://github.com/tokio-rs/axum/blob/axum-v0.7.2/axum/src/extract/ws.rs#L605>
fn from_tungstenite(message: ts::Message) -> Option<Message> {
    match message {
        ts::Message::Text(text) => Some(Message::Text(to_axum_utf8_bytes(text))),
        ts::Message::Binary(binary) => Some(Message::Binary(binary)),
        ts::Message::Ping(ping) => Some(Message::Ping(ping)),
        ts::Message::Pong(pong) => Some(Message::Pong(pong)),
        ts::Message::Close(Some(close)) => Some(Message::Close(Some(CloseFrame {
            code: close.code.into(),
            reason: to_axum_utf8_bytes(close.reason),
        }))),
        ts::Message::Close(None) => Some(Message::Close(None)),
        // we can ignore `Frame` frames as recommended by the tungstenite maintainers
        // https://github.com/snapview/tungstenite-rs/issues/268
        ts::Message::Frame(_) => None,
    }
}

fn to_axum_utf8_bytes(bytes: ts::protocol::frame::Utf8Bytes) -> axum::extract::ws::Utf8Bytes {
    axum::extract::ws::Utf8Bytes::from(bytes.as_str())
}
