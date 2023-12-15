use std::sync::Arc;

use axum::extract::ws::WebSocket;
use cotoami_db::Operator;
use futures::StreamExt;
use tokio::{sync::mpsc, task::JoinSet};
use tokio_util::sync::PollSender;
use tracing::debug;

use crate::{
    event::{handle_event_from_operator, NodeSentEvent},
    state::NodeState,
};

/// Events to be sent in [handle_operator]:
/// * local changes (which contains the changes from the parents of the local node)
/// * responses　(correlating with the number of children sending requests)
const SEND_BUFFER_SIZE: usize = 16;

pub(super) async fn handle_operator(socket: WebSocket, state: NodeState, opr: Operator) {
    let node_id = opr.node_id();

    let (mut sink, stream) = socket.split();
    let (sender, mut receiver) = mpsc::channel::<NodeSentEvent>(SEND_BUFFER_SIZE);
    let mut tasks = JoinSet::new();

    // A task sending events received from the other tasks
    tasks.spawn(async move {
        while let Some(event) = receiver.recv().await {
            if let Err(e) = super::send_event(&mut sink, event).await {
                debug!("Operator ({}) disconnected: {e}", node_id);
                break;
            }
        }
    });

    // A task publishing change events to a child node
    if let Operator::ChildNode(_) = opr {
        tasks.spawn({
            let sender = sender.clone();
            let mut changes = state.pubsub().local_changes().subscribe(None::<()>);
            async move {
                while let Some(change) = changes.next().await {
                    if let Err(_) = sender.send(NodeSentEvent::Change(change)).await {
                        // The task above has been terminated
                        break;
                    }
                }
            }
        });
    }

    // A task receiving events from the child
    tasks.spawn({
        let opr = Arc::new(opr);
        super::handle_message_stream(stream, node_id, move |event| {
            handle_event_from_operator(
                event,
                opr.clone(),
                state.clone(),
                PollSender::new(sender.clone()),
            )
        })
    });

    // If any one of the tasks exit, abort the others.
    if let Some(_) = tasks.join_next().await {
        tasks.shutdown().await;
    }
}
