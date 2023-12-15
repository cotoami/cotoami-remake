use axum::extract::ws::WebSocket;
use cotoami_db::ParentNode;
use futures::StreamExt;
use tokio::task::JoinSet;
use tracing::{debug, error};

use crate::{
    event::{handle_event_from_parent, NodeSentEvent},
    service::PubsubService,
    state::NodeState,
};

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
        move |event| handle_event_from_parent(event, parent_id, state.clone())
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
