use axum::extract::ws::WebSocket;
use cotoami_db::prelude::*;
use futures::StreamExt;
use tracing::{debug, error};

use crate::{event::NodeSentEvent, state::NodeState};

pub(super) async fn handle_child(socket: WebSocket, state: NodeState, operator: Operator) {
    let (mut sender, mut receiver) = socket.split();

    // Publish change events
    let mut changes = state.pubsub().local_changes().subscribe(None::<()>);
    let mut send_task = tokio::spawn(async move {
        while let Some(change) = changes.next().await {
            if let Err(e) = super::send_event(&mut sender, NodeSentEvent::Change(change)).await {
                debug!("Client ({}) disconnected: {e}", operator.node_id());
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
