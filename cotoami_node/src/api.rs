use axum::{
    extract::State,
    http::{Request, StatusCode},
    middleware::Next,
    response::{IntoResponse, Response},
    routing::get,
    Router,
};
use axum_extra::extract::cookie::CookieJar;
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

async fn root(State(_): State<AppState>) -> &'static str { "Cotoami Node API" }

async fn auth<B>(jar: CookieJar, request: Request<B>, next: Next<B>) -> Response {
    if let Some(session_token) = jar.get("session_token") {
        next.run(request).await.into_response()
    } else {
        StatusCode::UNAUTHORIZED.into_response()
    }
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
