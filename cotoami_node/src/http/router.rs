use axum::{
    extract::{Query, State},
    http::{StatusCode, Uri},
    middleware,
    response::IntoResponse,
    routing::get,
    Extension, Router,
};
use cotoami_db::prelude::*;

use super::*;
use crate::{
    api,
    api::error::{ApiError, IntoApiResult},
    AppState,
};

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
        .nest(
            "/changes",
            Router::new()
                .route("/", get(chunk_of_changes))
                .layer(middleware::from_fn(super::require_session)),
        )
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
    api::nodes::local_node(state.db).await.map(Json)
}

#[derive(serde::Serialize, serde::Deserialize, Validate)]
pub(crate) struct Position {
    #[validate(required, range(min = 1))]
    pub from: Option<i64>,
}

async fn chunk_of_changes(
    State(state): State<AppState>,
    Query(position): Query<Position>,
) -> Result<Json<api::changes::ChangesResult>, ApiError> {
    if let Err(errors) = position.validate() {
        return ("changes", errors).into_result();
    }
    let from = position.from.unwrap_or_else(|| unreachable!());
    let chunk_size = state.config.changes_chunk_size;
    api::changes::chunk_of_changes(from, chunk_size, state.db)
        .await
        .map(Json)
}
