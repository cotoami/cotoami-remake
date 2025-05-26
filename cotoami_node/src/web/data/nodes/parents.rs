use std::{collections::HashMap, sync::Arc};

use axum::{
    extract::{Extension, Path, State},
    routing::{get, put},
    Router,
};
use axum_extra::TypedHeader;
use chrono::NaiveDateTime;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{
    service::ServiceError,
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/others-last-posted-at", get(others_last_posted_at))
        .route("/{node_id}/fork", put(fork_from_parent))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/parents/others-last-posted-at
/////////////////////////////////////////////////////////////////////////////

async fn others_last_posted_at(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
) -> Result<Content<HashMap<Id<Node>, Option<NaiveDateTime>>>, ServiceError> {
    state
        .others_last_posted_at(Arc::new(operator))
        .await
        .map(|map| Content(map, accept))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/nodes/parents/:node_id/fork
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct Forked {
    affected: usize,
}

async fn fork_from_parent(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(node_id): Path<Id<Node>>,
) -> Result<Content<Forked>, ServiceError> {
    state.server_conns().try_get(&node_id)?.disable().await;

    let (affected, change) = spawn_blocking({
        let db = state.db().clone();
        move || db.new_session()?.fork_from(&node_id, &operator)
    })
    .await??;
    state.pubsub().publish_change(change);

    Ok(Content(Forked { affected }, accept))
}
