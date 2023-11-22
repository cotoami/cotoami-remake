use std::convert::Infallible;

use axum::{
    extract::{Path, Query, State},
    response::sse::{Event, KeepAlive, Sse},
};
use cotoami_db::prelude::*;
use futures::stream::Stream;

use super::*;
use crate::{
    service::{error::IntoServiceResult, models::ChunkOfChanges, ServiceError},
    state::NodeState,
};

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/local
/////////////////////////////////////////////////////////////////////////////

pub async fn local_node(State(state): State<NodeState>) -> Result<Json<Node>, ServiceError> {
    state
        .local_node()
        .await
        .map(Json)
        .map_err(ServiceError::from)
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/changes
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize, serde::Deserialize, Validate)]
pub(crate) struct Position {
    #[validate(required, range(min = 1))]
    pub from: Option<i64>,
}

pub(crate) async fn chunk_of_changes(
    State(state): State<NodeState>,
    Query(position): Query<Position>,
) -> Result<Json<ChunkOfChanges>, ServiceError> {
    if let Err(errors) = position.validate() {
        return ("changes", errors).into_result();
    }
    let from = position.from.unwrap_or_else(|| unreachable!());
    state
        .chunk_of_changes(from)
        .await
        .map(Json)
        .map_err(ServiceError::from)
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/events
/////////////////////////////////////////////////////////////////////////////

pub async fn stream_events(
    State(state): State<NodeState>,
    Extension(_session): Extension<ClientSession>,
) -> Sse<impl Stream<Item = Result<Event, Infallible>>> {
    // FIXME: subscribe to changes or requests
    let sub = state.pubsub().sse_change.subscribe(None::<()>);
    Sse::new(sub).keep_alive(KeepAlive::default())
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/nodes/parents/:node_id/fork
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
pub(crate) struct Forked {
    affected: usize,
}

pub(crate) async fn fork_from_parent(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    Path(node_id): Path<Id<Node>>,
) -> Result<Json<Forked>, ServiceError> {
    state.server_conn(&node_id)?.disable_sse();

    let db = state.db().clone();
    let (affected, change) =
        spawn_blocking(move || db.new_session()?.fork_from(&node_id, &operator)).await??;
    state.pubsub().publish_change(change);

    Ok(Json(Forked { affected }))
}
