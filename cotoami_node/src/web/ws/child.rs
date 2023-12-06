use std::ops::ControlFlow;

use axum::extract::ws::{Message, WebSocket};
use cotoami_db::Operator;
use futures::StreamExt;
use tokio::{
    sync::mpsc::{self, Sender},
    task::JoinSet,
};
use tower_service::Service;
use tracing::{debug, error, info};

use crate::{event::NodeSentEvent, state::NodeState};

/// Events to be sent in [handle_child]:
/// * local changes (which contains the changes from the parents of the local node)
/// * responses　(correlating with the number of children)
const SEND_BUFFER_SIZE: usize = 16;

pub(super) async fn handle_child(socket: WebSocket, mut state: NodeState, opr: Operator) {
    let node_id = opr.node_id();

    let (mut sink, mut stream) = socket.split();
    let (sender, mut receiver) = mpsc::channel::<NodeSentEvent>(SEND_BUFFER_SIZE);
    let mut tasks = JoinSet::new();

    // A task sending events received from the other tasks
    tasks.spawn(async move {
        while let Some(event) = receiver.recv().await {
            if let Err(e) = super::send_event(&mut sink, event).await {
                debug!("Child ({}) disconnected: {e}", node_id);
                break;
            }
        }
    });

    // A task publishing change events
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

    // A task receiving events from the child
    tasks.spawn(async move {
        while let Some(Ok(msg)) = stream.next().await {
            match msg {
                Message::Binary(vec) => match rmp_serde::from_slice::<NodeSentEvent>(&vec) {
                    Ok(event) => {
                        if handle_event(event, &mut state, opr.clone(), &sender)
                            .await
                            .is_break()
                        {
                            break;
                        }
                    }
                    Err(e) => {
                        // A malicious client can send an invalid message intentionally,
                        // so let's not handle it as an error.
                        info!("Child ({node_id}) sent an invalid binary message: {e}");
                        break;
                    }
                },
                Message::Close(c) => {
                    info!("Child ({node_id}) sent close with: {c:?}");
                    break;
                }
                the_others => debug!("Message ignored: {:?}", the_others),
            }
        }
    });

    // If any one of the tasks exit, abort the others.
    if let Some(_) = tasks.join_next().await {
        tasks.shutdown().await;
    }
}

async fn handle_event(
    event: NodeSentEvent,
    state: &mut NodeState,
    opr: Operator,
    sender: &Sender<NodeSentEvent>,
) -> ControlFlow<(), ()> {
    match event {
        NodeSentEvent::Request(mut request) => {
            debug!("Received a request from: {:?}", opr);
            request.set_from(opr);
            match state.call(request).await {
                Ok(response) => {
                    if sender
                        .send(NodeSentEvent::Response(response))
                        .await
                        .is_err()
                    {
                        // Disconnected
                        return ControlFlow::Break(());
                    }
                }
                Err(e) => {
                    // It shouldn't happen: an error processing a request
                    // should be stored in a response.
                    error!("Unexpected error: {}", e);
                }
            }
        }
        unsupported => {
            info!("Parent doesn't support the event: {:?}", unsupported);
        }
    }
    ControlFlow::Continue(())
}
