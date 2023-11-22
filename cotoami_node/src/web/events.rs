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
    service::{Event, ServiceError},
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
    let sub = state.pubsub().sse_change.subscribe(None::<()>);
    Sse::new(sub).keep_alive(KeepAlive::default())
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
        let event: Event = rmp_serde::from_slice(&body)?;

        // TODO:
        // state
        //     .handle_event(parent.node_id, event, source_service)
        //     .await?;

        // it won't create an "event" resouce, just handle it,
        // so returns `OK` instead of `Created`q
        Ok(StatusCode::OK)
    } else {
        // Only a parent can send an event
        Err(ServiceError::Permission)
    }
}
