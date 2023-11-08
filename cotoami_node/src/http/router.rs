use axum::{
    extract::State,
    http::{StatusCode, Uri},
    middleware,
    response::IntoResponse,
    routing::get,
    Extension, Router,
};
use cotoami_db::prelude::*;

use super::*;
use crate::{api::nodes, AppState};

pub(crate) fn router(state: AppState) -> Router {
    Router::new()
        .nest("/api", paths())
        .fallback(fallback)
        .layer(middleware::from_fn(csrf::protect_from_forgery))
        .layer(Extension(state.clone())) // for middleware
        .with_state(state)
}

fn paths() -> Router<AppState> {
    Router::new()
        .route("/", get(|| async { "Cotoami Node API" }))
        .nest("/session", session::routes())
        .nest("/events", events::routes())
        .nest("/changes", changes::routes())
        .nest(
            "/nodes",
            Router::new()
                .route("/local", get(local_node))
                .nest("/parents", super::nodes::parents::routes())
                .nest("/children", super::nodes::children::routes())
                .layer(middleware::from_fn(super::require_session)),
        )
        .nest("/cotos", cotos::routes())
        .nest("/cotonomas", cotonomas::routes())
}

async fn fallback(uri: Uri) -> impl IntoResponse {
    (StatusCode::NOT_FOUND, format!("No route: {}", uri.path()))
}

async fn local_node(State(state): State<AppState>) -> Result<Json<Node>, ApiError> {
    nodes::local_node(state.db).await.map(Json)
}
