use std::{convert::Infallible, net::SocketAddr};

use axum::{
    extract::{ConnectInfo, Extension, State},
    http::StatusCode,
    middleware,
    response::sse::{Event as SseEvent, KeepAlive, Sse},
    routing::get,
    Router,
};
use cotoami_db::prelude::*;
use futures::{future::AbortHandle, stream::Stream, StreamExt};
use tokio::sync::oneshot;
use tracing::debug;

use crate::{
    event::remote::NodeSentEvent,
    service::{PubsubService, ServiceError},
    state::{ClientConnection, NodeState},
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
    ConnectInfo(remote_addr): ConnectInfo<SocketAddr>,
    State(state): State<NodeState>,
    Extension(session): Extension<ClientSession>,
) -> Sse<impl Stream<Item = Result<SseEvent, Infallible>>> {
    let client_id = session.client_node_id();

    // Create a event stream
    let events = match &session {
        ClientSession::ParentNode(parent) => {
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
                .map(|request| sse_event(NodeSentEvent::NAME_REQUEST, request))
                .boxed()
        }
        ClientSession::Operator(opr) => {
            // Stream of change events
            let changes = state
                .pubsub()
                .changes()
                .subscribe(None::<()>)
                .map(|change| sse_event(NodeSentEvent::NAME_CHANGE, change));

            if opr.has_owner_permission() {
                // Stream of local events
                let local_events = state
                    .pubsub()
                    .events()
                    .subscribe(None::<()>)
                    .map(|local_event| sse_event(NodeSentEvent::NAME_REMOTE_LOCAL, local_event));
                futures::stream::select(changes, local_events).boxed()
            } else {
                changes.boxed()
            }
        }
    };

    // Put a StreamLocal in the event stream to detect disconnection
    let events = {
        let state = state.clone();
        async_stream::stream! {
            // How to know when Sse connection is closed?
            // https://github.com/tokio-rs/axum/discussions/1060
            let _local = StreamLocal(session, state);
            for await event in events { yield event; }
        }
    };

    // Register a connection for a non-anonymous client
    let (events, abort_events) = futures::stream::abortable(events);
    match client_id {
        Some(client_id) => register_client_conn(&state, client_id, remote_addr, abort_events),
        None => (),
    }

    Sse::new(events).keep_alive(KeepAlive::default())
}

fn register_client_conn(
    state: &NodeState,
    client_id: Id<Node>,
    remote_addr: SocketAddr,
    abort_events: AbortHandle,
) {
    let (tx_disconnect, rx_disconnect) = oneshot::channel::<()>();
    tokio::spawn({
        let state = state.clone();
        async move {
            match rx_disconnect.await {
                Ok(_) => {
                    debug!("Disconnecting a SSE client {client_id} ...",);
                    state.clear_client_node_session(client_id).await.unwrap();
                    abort_events.abort();
                }
                Err(_) => (), // the sender dropped
            }
        }
    });
    state.put_client_conn(ClientConnection::new(
        client_id,
        remote_addr.ip().to_string(),
        tx_disconnect,
    ));
}

fn sse_event<T, D>(event_type: T, data: D) -> Result<SseEvent, Infallible>
where
    T: AsRef<str>,
    D: serde::Serialize,
{
    match SseEvent::default().event(event_type).json_data(data) {
        Ok(event) => Ok(event),
        Err(e) => Ok(error_sse_event(e)),
    }
}

fn error_sse_event<E: ToString>(e: E) -> SseEvent {
    SseEvent::default()
        .event(NodeSentEvent::NAME_ERROR)
        .data(e.to_string())
}

struct StreamLocal(ClientSession, NodeState);
impl Drop for StreamLocal {
    fn drop(&mut self) {
        let Self(session, state) = self;
        if let Some(client_id) = session.client_node_id() {
            state.remove_client_conn(client_id, None);
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/events
/////////////////////////////////////////////////////////////////////////////

/// Send a [NodeSentEvent] from parent(client) to child(server).
async fn post_event(
    State(state): State<NodeState>,
    Extension(session): Extension<ClientSession>,
    body: axum::body::Bytes,
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
                let response_id = *response.id();
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
