use axum::extract::ws::{Message, WebSocket};
use cotoami_db::prelude::*;
use futures::StreamExt;
use tracing::{debug, error, info};

use crate::{event::NodeSentEvent, state::NodeState};

pub(super) async fn handle_child(socket: WebSocket, state: NodeState, operator: Operator) {
    let (mut sender, mut receiver) = socket.split();

    // Publish change events
    let node_id = operator.node_id();
    let mut changes = state.pubsub().local_changes().subscribe(None::<()>);
    let mut send_task = tokio::spawn(async move {
        while let Some(change) = changes.next().await {
            if let Err(e) = super::send_event(&mut sender, NodeSentEvent::Change(change)).await {
                debug!("Client ({}) disconnected: {e}", node_id);
            }
        }
    });

    // Accept request events
    let mut recv_task = tokio::spawn(async move {
        while let Some(Ok(msg)) = receiver.next().await {
            match msg {
                Message::Binary(vec) => match rmp_serde::from_slice::<NodeSentEvent>(&vec) {
                    Ok(event) => {
                        handle_event(event, state.clone(), operator.clone()).await;
                    }
                    Err(e) => {
                        // A malicious　client can send invalid message intentionally,
                        // so let's not handle it as errors.
                        info!("Child ({node_id}) sent an invalid binary message: {e}");
                        return;
                    }
                },
                Message::Close(c) => {
                    info!("Child ({node_id}) sent close with: {c:?}");
                    return;
                }
                _ => debug!(""),
            }
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

async fn handle_event(event: NodeSentEvent, state: NodeState, operator: Operator) {
    //
}
