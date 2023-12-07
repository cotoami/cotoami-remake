use std::ops::ControlFlow;

use axum::extract::ws::WebSocket;
use cotoami_db::{DatabaseError, Id, Node, ParentNode};
use futures::StreamExt;
use tokio::task::JoinSet;
use tracing::{debug, error, info};

use crate::{event::NodeSentEvent, service::PubsubService, state::NodeState};

pub(super) async fn handle_parent(socket: WebSocket, state: NodeState, parent: ParentNode) {
    let parent_id = parent.node_id;

    let (mut sink, stream) = socket.split();
    let mut tasks = JoinSet::new();

    // Register the WebSocket client-as-parent as a service
    let parent_service = PubsubService::new(
        format!("WebSocket client-as-parent: {}", parent_id),
        state.pubsub().responses().clone(),
    );
    state.put_parent_service(parent_id, Box::new(parent_service.clone()));

    // A task sending request events
    tasks.spawn({
        let mut requests = parent_service.requests().subscribe(None::<()>);
        async move {
            while let Some(request) = requests.next().await {
                let event = NodeSentEvent::Request(request);
                if let Err(e) = super::send_event(&mut sink, event).await {
                    debug!("Parent ({}) disconnected: {e}", parent_id);
                    break;
                }
            }
        }
    });

    // A task receiving events from the parent
    tasks.spawn(super::handle_message_stream(stream, parent_id, {
        let state = state.clone();
        move |event| handle_event(event, state.clone(), parent_id)
    }));

    // Sync with the parent after tasks are setup.
    //
    // NOTE:
    // Even if the parent_service is available here, the connection could be closed
    // by this time since deregistering the service will be done below after all the tasks
    // are shutdown. If the service is corrupted, a request will be timed out in
    // `sync_with_parent` and all the tasks will be shutdown anyway.
    if let Some(parent_service) = state.parent_service(&parent.node_id) {
        if let Err(e) = state.sync_with_parent(parent_id, parent_service).await {
            error!("Error syncing with ({}): {}", parent.node_id, e);
            tasks.shutdown().await;
            return;
        }
    }

    // If any one of the tasks exit, abort the other.
    if let Some(_) = tasks.join_next().await {
        tasks.shutdown().await;
        state
            .pubsub()
            .events()
            .publish_parent_disconnected(parent_id);
    }
}

async fn handle_event(
    event: NodeSentEvent,
    state: NodeState,
    parent_id: Id<Node>,
) -> ControlFlow<(), ()> {
    match event {
        NodeSentEvent::Change(change) => {
            if let Some(parent_service) = state.parent_service(&parent_id) {
                let r = state
                    .handle_parent_change(parent_id, change, parent_service)
                    .await;
                if let Err(e) = r {
                    // `sync_with_parent` could be run in parallel, in such cases,
                    // `DatabaseError::UnexpectedChangeNumber` will be returned.
                    if let Some(DatabaseError::UnexpectedChangeNumber { .. }) =
                        e.downcast_ref::<DatabaseError>()
                    {
                        info!("Already running sync_with_parent: {e}");
                    } else {
                        error!("Error applying a change from the parent ({parent_id}): {e}");
                        return ControlFlow::Break(());
                    }
                }
            }
        }
        NodeSentEvent::Response(response) => {
            debug!("Received a response from {}", parent_id);
            let response_id = response.id().clone();
            state
                .pubsub()
                .responses()
                .publish(response, Some(&response_id))
        }
        unsupported => {
            info!("Child doesn't support the event: {:?}", unsupported);
        }
    }
    ControlFlow::Continue(())
}
