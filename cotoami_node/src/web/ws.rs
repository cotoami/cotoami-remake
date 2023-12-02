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
use futures::{SinkExt, StreamExt};
use futures_util::stream::SplitSink;
use tracing::{debug, error};

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
        let (mut sender, mut receiver) = socket.split();

        // Publish change events
        let mut changes = state.pubsub().local_changes().subscribe(None::<()>);
        let mut send_task = tokio::spawn(async move {
            while let Some(change) = changes.next().await {
                if let Err(e) = send_event(&mut sender, NodeSentEvent::Change(change)).await {
                    debug!("Client ({}) disconnected: {e}", session.client_node_id());
                }
            }
        });

        // Accept request events
        let mut recv_task = tokio::spawn(async move {
            while let Some(Ok(message)) = receiver.next().await {
                //
            }
        });

        // If any one of the tasks exit, abort the other.
        tokio::select! {
            result = (&mut send_task) => {
                if let Err(e) = result {
                    error!("Error publishing changes: {}", e);
                }
                recv_task.abort();
            },
            result = (&mut recv_task) => {
                if let Err(e) = result {
                    error!("Error accepting requests: {}", e);
                }
                send_task.abort();
            }
        }
    }
}

async fn send_event(
    sender: &mut SplitSink<WebSocket, Message>,
    event: NodeSentEvent,
) -> Result<(), axum::Error> {
    let bytes = rmp_serde::to_vec(&event)
        .map(Bytes::from)
        .expect("A NodeSentEvent should be serializable into MessagePack");
    sender.send(Message::Binary(bytes.into())).await
}
