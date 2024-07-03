use std::convert::Infallible;

use axum::{
    body::Bytes,
    extract::{Extension, State},
    http::StatusCode,
    middleware,
    response::sse::{Event as SseEvent, KeepAlive, Sse},
    routing::get,
    Router,
};
use cotoami_db::prelude::*;
use futures::{stream::Stream, StreamExt};
use tracing::debug;

use crate::{
    event::remote::NodeSentEvent,
    service::{PubsubService, ServiceError},
    state::{EventPubsub, NodeState},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(stream_events).post(post_event))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/events
/////////////////////////////////////////////////////////////////////////////

async fn stream_events(
    State(state): State<NodeState>,
    Extension(session): Extension<ClientSession>,
) -> Sse<impl Stream<Item = Result<SseEvent, Infallible>>> {
    let events = match session {
        ClientSession::ParentNode(parent) => {
            // How to know when Sse connection is closed?
            // https://github.com/tokio-rs/axum/discussions/1060
            struct StreamLocal(Id<Node>, EventPubsub);
            impl Drop for StreamLocal {
                fn drop(&mut self) {
                    let Self(parent_id, events) = self;
                    debug!("SSE client-as-parent stream closed: {}", parent_id);
                    events.parent_disconnected(*parent_id);
                }
            }
            let _local = StreamLocal(parent.node_id, state.pubsub().events().clone());

            // Create a SSE client-as-parent service
            let parent_service = PubsubService::new(
                format!("SSE client-as-parent: {}", parent.node_id),
                state.pubsub().responses().clone(),
            );
            let requests = parent_service.requests().subscribe(None::<()>);

            // Register the parent service
            state.register_parent_service(parent.node_id, Box::new(parent_service.clone()));

            // Stream `request` events
            requests
                .map(|request| event(NodeSentEvent::NAME_REQUEST, request))
                .boxed()
        }
        ClientSession::Operator(opr) => {
            // Stream of change events
            let changes = state
                .pubsub()
                .changes()
                .subscribe(None::<()>)
                .map(|change| event(NodeSentEvent::NAME_CHANGE, change));

            if opr.has_owner_permission() {
                // Stream of local events
                let local_events = state
                    .pubsub()
                    .events()
                    .subscribe(None::<()>)
                    .map(|local_event| event(NodeSentEvent::NAME_REMOTE_LOCAL, local_event));
                futures::stream::select(changes, local_events).boxed()
            } else {
                changes.boxed()
            }
        }
    };
    Sse::new(events).keep_alive(KeepAlive::default())
}

fn event<T, D>(event_type: T, data: D) -> Result<SseEvent, Infallible>
where
    T: AsRef<str>,
    D: serde::Serialize,
{
    match SseEvent::default().event(event_type).json_data(data) {
        Ok(event) => Ok(event),
        Err(e) => Ok(error_event(e)),
    }
}

fn error_event<E: ToString>(e: E) -> SseEvent {
    SseEvent::default()
        .event(NodeSentEvent::NAME_ERROR)
        .data(e.to_string())
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/events
/////////////////////////////////////////////////////////////////////////////

/// Send a [NodeSentEvent] from parent(client) to child(server).
async fn post_event(
    State(state): State<NodeState>,
    Extension(session): Extension<ClientSession>,
    body: Bytes,
) -> Result<StatusCode, ServiceError> {
    if let ClientSession::ParentNode(parent) = session {
        let parent_service = state.parent_services().try_get(&parent.node_id)?;
        let event = rmp_serde::from_slice(&body).map_err(|_| {
            ServiceError::request(
                "invalid-request-body",
                "The request body couldn't be deserialized into NodeSentEvent.",
            )
        })?;
        match event {
            NodeSentEvent::Change(change) => {
                // `sync_with_parent` could be run in parallel, in such cases,
                // it will return `DatabaseError::UnexpectedChangeNumber`, which
                // will be converted into an internal server error in HTTP.
                state
                    .handle_parent_change(parent.node_id, change, parent_service)
                    .await?;
            }
            NodeSentEvent::Request(_) => {
                // Sending requests via this API is not supported.
                return Err(ServiceError::NotImplemented);
            }
            NodeSentEvent::Response(response) => {
                debug!("Received a response from {}", parent_service.description());
                let response_id = response.id().clone();
                state
                    .pubsub()
                    .responses()
                    .publish(response, Some(&response_id))
            }
            NodeSentEvent::RemoteLocal(event) => state
                .pubsub()
                .remote_events()
                .publish(event, Some(&parent.node_id)),
            NodeSentEvent::Error(msg) => {
                return Err(ServiceError::Server(msg));
            }
        }

        // It won't create an "event" resouce, just handle it,
        // so returns `OK` instead of `Created`.
        Ok(StatusCode::OK)
    } else {
        // Only a parent node can send an event via HTTP request
        Err(ServiceError::Permission)
    }
}
