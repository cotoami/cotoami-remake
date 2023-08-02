use std::convert::Infallible;

use axum::{
    extract::State,
    response::sse::{Event, KeepAlive, Sse},
    routing::get,
    Router,
};
use futures::stream::Stream;
use validator::Validate;

use crate::AppState;

mod cotos;
mod nodes;

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", get(root))
        .route("/events", get(stream_events))
        .nest("/nodes", nodes::routes())
        .nest("/cotos", cotos::routes())
}

pub(super) async fn root(State(_): State<AppState>) -> &'static str { "Cotoami Node API" }

async fn stream_events(
    State(state): State<AppState>,
) -> Sse<impl Stream<Item = Result<Event, Infallible>>> {
    let sub = state.pubsub.lock().subscribe();
    Sse::new(sub).keep_alive(KeepAlive::default())
}

/////////////////////////////////////////////////////////////////////////////
// Pagination Query
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct Pagination {
    #[serde(default)]
    page: i64,

    #[validate(range(min = 1, max = 1000))]
    page_size: Option<i64>,
}
