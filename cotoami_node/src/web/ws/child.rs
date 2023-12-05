use axum::extract::ws::{Message, WebSocket};
use cotoami_db::prelude::*;
use futures::StreamExt;
use tokio::{
    sync::{
        mpsc,
        mpsc::{error::SendError, Sender},
    },
    task::JoinSet,
};
use tower_service::Service;
use tracing::{debug, error, info};

use crate::{event::NodeSentEvent, state::NodeState};

pub(super) async fn handle_child(socket: WebSocket, state: NodeState, operator: Operator) {
    let node_id = operator.node_id();

    let (mut sink, mut stream) = socket.split();
    let (sender, mut receiver) = mpsc::channel::<NodeSentEvent>(16); // not sure of an appropriate buffer size
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
                        if let Err(_) =
                            handle_event(event, state.clone(), operator.clone(), &sender).await
                        {
                            break;
                        }
                    }
                    Err(e) => {
                        // A malicious　client can send invalid message intentionally,
                        // so let's not handle it as errors.
                        info!("Child ({node_id}) sent an invalid binary message: {e}");
                        break;
                    }
                },
                Message::Close(c) => {
                    info!("Child ({node_id}) sent close with: {c:?}");
                    break;
                }
                _ => debug!(""),
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
    mut state: NodeState,
    operator: Operator,
    sender: &Sender<NodeSentEvent>,
) -> Result<(), SendError<NodeSentEvent>> {
    match event {
        NodeSentEvent::Request(request) => {
            debug!("Received a request from {:?}", operator);
            match state.call(request).await {
                Ok(response) => {
                    sender.send(NodeSentEvent::Response(response)).await?;
                }
                Err(e) => {
                    // An error processing a request should be stored in a response.
                    error!("Unexpected error: {}", e);
                }
            }
        }
        unsupported => {
            info!("Parent doesn't support the event: {:?}", unsupported);
        }
    }
    Ok(())
}
