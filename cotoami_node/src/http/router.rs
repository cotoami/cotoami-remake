use std::convert::Infallible;

use axum::{
    extract::{Path, Query, State},
    http::{StatusCode, Uri},
    middleware,
    response::{
        sse::{Event, KeepAlive, Sse},
        IntoResponse,
    },
    routing::{delete, get, put},
    Extension, Router,
};
use cotoami_db::prelude::*;
use futures::stream::Stream;

use super::*;
use crate::{
    api,
    api::{
        changes::ChunkOfChanges,
        error::{ApiError, IntoApiResult},
    },
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
        .nest(
            "/session",
            Router::new()
                .route("/", delete(super::session::delete_session))
                .route_layer(middleware::from_fn(super::require_session))
                .route("/owner", put(super::session::create_owner_session))
                .route(
                    "/client-node",
                    put(super::session::create_client_node_session),
                ),
        )
        .nest(
            "/events",
            Router::new()
                .route("/", get(stream_events))
                .layer(middleware::from_fn(super::require_session)),
        )
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
                .nest(
                    "/servers",
                    Router::new()
                        .route(
                            "/",
                            get(super::servers::all_servers).post(super::servers::add_server_node),
                        )
                        .route("/:node_id", put(super::servers::update_server_node))
                        .route("/:node_id/fork", put(fork_from_parent))
                        .layer(middleware::from_fn(require_session)),
                )
                .nest(
                    "/clients",
                    Router::new()
                        .route(
                            "/",
                            get(super::clients::recent_client_nodes)
                                .post(super::clients::add_client_node),
                        )
                        .layer(middleware::from_fn(require_session)),
                )
                .nest(
                    "parents",
                    Router::new()
                        .route("/:node_id/fork", put(fork_from_parent))
                        .layer(middleware::from_fn(require_session)),
                )
                .layer(middleware::from_fn(super::require_session)),
        )
        .nest("/cotos", cotos::routes())
        .nest("/cotonomas", cotonomas::routes())
}

async fn fallback(uri: Uri) -> impl IntoResponse {
    (StatusCode::NOT_FOUND, format!("No route: {}", uri.path()))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/local
/////////////////////////////////////////////////////////////////////////////

async fn local_node(State(state): State<AppState>) -> Result<Json<Node>, ApiError> {
    state.local_node().await.map(Json).map_err(ApiError::from)
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/changes
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize, serde::Deserialize, Validate)]
pub(crate) struct Position {
    #[validate(required, range(min = 1))]
    pub from: Option<i64>,
}

async fn chunk_of_changes(
    State(state): State<AppState>,
    Query(position): Query<Position>,
) -> Result<Json<ChunkOfChanges>, ApiError> {
    if let Err(errors) = position.validate() {
        return ("changes", errors).into_result();
    }
    let from = position.from.unwrap_or_else(|| unreachable!());
    let chunk_size = state.config().changes_chunk_size;
    api::changes::chunk_of_changes(from, chunk_size, state.db().clone())
        .await
        .map(Json)
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/events
/////////////////////////////////////////////////////////////////////////////

async fn stream_events(
    State(state): State<AppState>,
    Extension(_session): Extension<ClientSession>,
) -> Sse<impl Stream<Item = Result<Event, Infallible>>> {
    // FIXME: subscribe to changes or requests
    let sub = state.pubsub.sse_change.subscribe(None::<()>);
    Sse::new(sub).keep_alive(KeepAlive::default())
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/nodes/parents/:node_id/fork
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct Forked {
    affected: usize,
}

async fn fork_from_parent(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Path(node_id): Path<Id<Node>>,
) -> Result<Json<Forked>, ApiError> {
    state.server_conn(&node_id)?.disable_sse();

    let (affected, change) = spawn_blocking(move || {
        let db = state.db.new_session()?;
        db.fork_from(&node_id, &operator)
    })
    .await??;
    state.pubsub.publish_change(change);

    Ok(Json(Forked { affected }))
}
