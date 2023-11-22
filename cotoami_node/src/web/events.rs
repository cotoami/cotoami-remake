use std::convert::Infallible;

use axum::{
    extract::State,
    response::sse::{Event, KeepAlive, Sse},
};
use cotoami_db::prelude::*;
use futures::stream::Stream;

use super::*;
use crate::state::NodeState;

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(stream_events))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/events
/////////////////////////////////////////////////////////////////////////////

async fn stream_events(
    State(state): State<NodeState>,
    Extension(_session): Extension<ClientSession>,
) -> Sse<impl Stream<Item = Result<Event, Infallible>>> {
    // FIXME: subscribe to changes or requests
    let sub = state.pubsub().sse_change.subscribe(None::<()>);
    Sse::new(sub).keep_alive(KeepAlive::default())
}
