use std::convert::Infallible;

use async_stream::stream;
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
use futures::stream::Stream;
use tracing::{debug, error};

use crate::{
    event::NodeSentEvent,
    service::{PubsubService, ServiceError},
    state::EventPubsub,
    NodeState,
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
    let events = stream! {
        if let ClientSession::ParentNode(parent) = session {
            // How to know when Sse connection is closed?
            // https://github.com/tokio-rs/axum/discussions/1060
            struct StreamLocal(Id<Node>, EventPubsub);
            impl Drop for StreamLocal {
                fn drop(&mut self) {
                    let Self(parent_id, events) = self;
                    debug!("SSE client-as-parent stream closed: {}", parent_id);
                    events.publish_parent_disconnected(*parent_id);
                }
            }
            let _local = StreamLocal(parent.node_id, state.pubsub().events().clone());

            // Register the SSE client-as-parent as a service
            let parent_service = PubsubService::new(
                format!("SSE client-as-parent: {}", parent.node_id),
                state.pubsub().responses().clone(),
            );
            state.put_parent_service(parent.node_id, Box::new(parent_service.clone()));

            // Stream `request` events
            let requests = parent_service.requests().subscribe(None::<()>);
            for await request in requests {
                yield event("request", request);
            }
        } else {
            // Stream `change` events
            let changes = state.pubsub().local_changes().subscribe(None::<()>);
            for await change in changes {
                yield event("change", change);
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
    SseEvent::default().event("error").data(e.to_string())
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/events
/////////////////////////////////////////////////////////////////////////////

async fn post_event(
    State(state): State<NodeState>,
    Extension(session): Extension<ClientSession>,
    body: Bytes,
) -> Result<StatusCode, ServiceError> {
    if let ClientSession::ParentNode(parent) = session {
        let parent_service = state.parent_service_or_err(&parent.node_id)?;
        let event = rmp_serde::from_slice(&body)
            .map_err(|_| ServiceError::request("invalid-request-body"))?;
        match event {
            NodeSentEvent::Connected => {
                // Run database-syncing in another thread, otherwise a deadlock will occur:
                // the event loop in the SSE client is blocked until this API responds,
                // which then blocks `sync_with_parent`.
                tokio::spawn(async move {
                    if let Err(e) = state.sync_with_parent(parent.node_id, parent_service).await {
                        error!("Error syncing with ({}): {}", parent.node_id, e);
                    }
                });
            }
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
