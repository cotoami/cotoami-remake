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
use futures::stream::Stream;

use crate::{
    service::{models::NodeSentEvent, ServiceError},
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
    Extension(_session): Extension<ClientSession>,
) -> Sse<impl Stream<Item = Result<SseEvent, Infallible>>> {
    // FIXME: subscribe to changes or requests
    let sub = state.pubsub().sse_changes.subscribe(None::<()>);
    Sse::new(sub).keep_alive(KeepAlive::default())
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/events
/////////////////////////////////////////////////////////////////////////////

async fn post_event(
    State(mut state): State<NodeState>,
    Extension(session): Extension<ClientSession>,
    body: Bytes,
) -> Result<StatusCode, ServiceError> {
    if let ClientSession::ParentNode(parent) = session {
        let event: NodeSentEvent = rmp_serde::from_slice(&body)?;
        let parent_service = state.parent_service_or_err(&parent.node_id)?;
        state
            .handle_event(parent.node_id, event, parent_service)
            .await?;

        // It won't create an "event" resouce, just handle it,
        // so returns `OK` instead of `Created`.
        Ok(StatusCode::OK)
    } else {
        // Only a parent node can send an event via HTTP request
        Err(ServiceError::Permission)
    }
}
