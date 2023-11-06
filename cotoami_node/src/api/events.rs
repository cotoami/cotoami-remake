use std::convert::Infallible;

use axum::{
    extract::State,
    middleware,
    response::sse::{Event, KeepAlive, Sse},
    routing::get,
    Router,
};
use futures::stream::Stream;

use crate::{AppState, SsePubsubTopic};

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", get(stream_events))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/events
/////////////////////////////////////////////////////////////////////////////

async fn stream_events(
    State(state): State<AppState>,
) -> Sse<impl Stream<Item = Result<Event, Infallible>>> {
    // FIXME: subscribe to changes or requests
    let sub = state
        .pubsub
        .sse
        .lock()
        .subscribe(Some(SsePubsubTopic::Change));
    Sse::new(sub).keep_alive(KeepAlive::default())
}
