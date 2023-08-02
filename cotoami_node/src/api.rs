use axum::{extract::State, routing::get, Router};
use validator::Validate;

use crate::AppState;

mod cotos;
mod events;
mod nodes;

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", get(root))
        .nest("/events", events::routes())
        .nest("/nodes", nodes::routes())
        .nest("/cotos", cotos::routes())
}

pub(super) async fn root(State(_): State<AppState>) -> &'static str { "Cotoami Node API" }

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
