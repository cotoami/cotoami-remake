use std::{ops::ControlFlow, sync::Arc};

use cotoami_db::{ChangelogEntry, DatabaseError, Id, Node, Operator};
use futures::{Sink, SinkExt};
use tracing::{debug, error, info};

use crate::{
    event::local::LocalNodeEvent,
    service::{error::ServiceError, Request, Response, Service},
    state::NodeState,
};

pub(crate) mod tungstenite;

/// An event to be sent between cotoami nodes.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub(crate) enum NodeSentEvent {
    Change(ChangelogEntry),
    Request(Request),
    Response(Response),
    RemoteLocal(LocalNodeEvent),
    Error(String),
}

impl NodeSentEvent {
    pub const NAME_CHANGE: &'static str = "change";
    pub const NAME_REQUEST: &'static str = "request";
    pub const NAME_RESPONSE: &'static str = "response";
    pub const NAME_REMOTE_LOCAL: &'static str = "remote-local";
    pub const NAME_ERROR: &'static str = "error";
}

#[derive(Debug, derive_more::Display)]
pub(crate) enum EventLoopError {
    #[display("Communication failed: {}", _0)]
    CommunicationFailed(anyhow::Error),

    #[display("Event handling failed: {}", _0)]
    EventHandlingFailed(anyhow::Error),
}

/// Handle events sent from an entity authenticated as an operator
/// (a child node or an owner of the local node).
///
/// This function is protocol-agnostic as long as the peer can be treated as a [Sink].
pub(crate) async fn handle_event_from_operator<S, E>(
    event: NodeSentEvent,
    opr: Arc<Operator>,
    state: NodeState,
    mut peer: S,
) -> ControlFlow<anyhow::Error>
where
    S: Sink<NodeSentEvent, Error = E> + Unpin,
    E: Into<anyhow::Error>,
{
    match event {
        NodeSentEvent::Request(mut request) => {
            debug!("Received a request from: {opr:?}: {request:?}");

            // Operate-as-owner feature
            if request.as_owner() {
                if opr.has_owner_permission() {
                    match state.db().globals().local_node_as_operator() {
                        Ok(owner) => {
                            request.set_from(Arc::new(owner));
                        }
                        Err(e) => {
                            return ControlFlow::Break(e);
                        }
                    }
                } else {
                    send_response(
                        Response::to(&request, Err(ServiceError::Permission)),
                        &mut peer,
                    )
                    .await?;
                }
            } else {
                request.set_from(opr);
            }

            match state.call(request).await {
                Ok(response) => {
                    send_response(response, &mut peer).await?;
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

pub(crate) async fn send_response<S, E>(
    response: Response,
    peer: &mut S,
) -> ControlFlow<anyhow::Error>
where
    S: Sink<NodeSentEvent, Error = E> + Unpin,
    E: Into<anyhow::Error>,
{
    if let Err(e) = peer.send(NodeSentEvent::Response(response)).await {
        // Disconnected
        ControlFlow::Break(e.into().context("Error sending a response."))
    } else {
        ControlFlow::Continue(())
    }
}

/// Handle events sent from a parent node.
///
/// This function is protocol-agnostic.
pub(crate) async fn handle_event_from_parent(
    event: NodeSentEvent,
    parent_id: Id<Node>,
    state: NodeState,
) -> ControlFlow<anyhow::Error> {
    match event {
        NodeSentEvent::Change(change) => {
            if let Some(parent_service) = state.parent_services().get(&parent_id) {
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
            let response_id = *response.id();
            state
                .pubsub()
                .responses()
                .publish(response, Some(&response_id))
        }
        NodeSentEvent::RemoteLocal(event) => state
            .pubsub()
            .remote_events()
            .publish(event, Some(&parent_id)),
        NodeSentEvent::Error(msg) => error!("Event error: {msg}"),
        unsupported => {
            info!("Child doesn't support the event: {:?}", unsupported);
        }
    }
    ControlFlow::Continue(())
}
