use std::{ops::ControlFlow, sync::Arc};

use cotoami_db::{ChangelogEntry, DatabaseError, Id, Node, Operator};
use futures::{Sink, SinkExt};
use tower_service::Service;
use tracing::{debug, error, info};

use crate::{
    service::{Request, Response},
    state::NodeState,
};

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub(crate) enum NodeSentEvent {
    Connected,
    Change(ChangelogEntry),
    Request(Request),
    Response(Response),
    Error(String),
}

pub(crate) async fn handle_event_from_operator<S, E>(
    event: NodeSentEvent,
    opr: Arc<Operator>,
    mut state: NodeState,
    mut sink: S,
) -> ControlFlow<anyhow::Error>
where
    S: Sink<NodeSentEvent, Error = E> + Unpin,
    E: Into<anyhow::Error>,
{
    match event {
        NodeSentEvent::Request(mut request) => {
            debug!("Received a request from: {opr:?}: {request:?}");
            request.set_from(opr);
            match state.call(request).await {
                Ok(response) => {
                    if let Err(e) = sink.send(NodeSentEvent::Response(response)).await {
                        // Disconnected
                        return ControlFlow::Break(e.into().context("Error sending a response."));
                    }
                }
                Err(e) => {
                    // It shouldn't happen: an error processing a request
                    // should be stored in a response.
                    error!("Unexpected error: {}", e);
                }
            }
        }
        NodeSentEvent::Error(msg) => error!("Event error: {msg}"),
        unsupported => {
            info!("Parent doesn't support the event: {:?}", unsupported);
        }
    }
    ControlFlow::Continue(())
}

pub(crate) async fn handle_event_from_parent(
    event: NodeSentEvent,
    parent_id: Id<Node>,
    state: NodeState,
) -> ControlFlow<anyhow::Error> {
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
                        return ControlFlow::Break(e);
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
        NodeSentEvent::Error(msg) => error!("Event error: {msg}"),
        unsupported => {
            info!("Child doesn't support the event: {:?}", unsupported);
        }
    }
    ControlFlow::Continue(())
}
