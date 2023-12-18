use std::{future::Future, marker::Unpin, ops::ControlFlow};

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
use futures::{Sink, SinkExt, Stream, StreamExt};
use tracing::{debug, info};

use crate::{event::NodeSentEvent, state::NodeState};

mod operator;
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
            operator::handle_operator(socket, state, opr).await;
        }
        ClientSession::ParentNode(parent) => {
            parent::handle_parent(socket, state, parent).await;
        }
    }
}

async fn handle_message_stream<S, H, F>(mut stream: S, client_id: Id<Node>, handler: H)
where
    S: Stream<Item = Result<Message, axum::Error>> + Unpin,
    H: Fn(NodeSentEvent) -> F,
    F: Future<Output = ControlFlow<anyhow::Error>>,
{
    while let Some(Ok(msg)) = stream.next().await {
        match msg {
            Message::Binary(vec) => match rmp_serde::from_slice::<NodeSentEvent>(&vec) {
                Ok(event) => {
                    if handler(event).await.is_break() {
                        break;
                    }
                }
                Err(e) => {
                    // A malicious client can send an invalid message intentionally,
                    // so let's not handle it as an error.
                    info!("Client ({client_id}) sent an invalid binary message: {e}");
                    break;
                }
            },
            Message::Close(c) => {
                info!("Client ({client_id}) sent close with: {c:?}");
                break;
            }
            the_others => debug!("Message ignored: {:?}", the_others),
        }
    }
}

async fn send_event<S, E>(mut message_sink: S, event: NodeSentEvent) -> Result<(), E>
where
    S: Sink<Message, Error = E> + Unpin,
{
    let bytes = rmp_serde::to_vec(&event)
        .map(Bytes::from)
        .expect("A NodeSentEvent should be serializable into MessagePack");
    message_sink.send(Message::Binary(bytes.into())).await
}
