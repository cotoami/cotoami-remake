use std::convert::Infallible;

use axum::{
    extract::State,
    response::sse::{Event, KeepAlive, Sse},
    routing::get,
    Router,
};
use futures::stream::Stream;

use crate::AppState;

pub(super) fn routes() -> Router<AppState> { Router::new().route("/", get(stream_events)) }

/////////////////////////////////////////////////////////////////////////////
// GET /api/events
/////////////////////////////////////////////////////////////////////////////

async fn stream_events(
    State(state): State<AppState>,
) -> Sse<impl Stream<Item = Result<Event, Infallible>>> {
    let sub = state.pubsub.lock().subscribe();
    Sse::new(sub).keep_alive(KeepAlive::default())
}
