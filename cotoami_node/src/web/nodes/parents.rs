use axum::{
    extract::{Extension, Path, State},
    routing::put,
    Json, Router,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{service::ServiceError, state::NodeState};

pub(super) fn routes() -> Router<NodeState> {
    Router::new().route("/:node_id/fork", put(fork_from_parent))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/nodes/parents/:node_id/fork
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct Forked {
    affected: usize,
}

async fn fork_from_parent(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    Path(node_id): Path<Id<Node>>,
) -> Result<Json<Forked>, ServiceError> {
    state.server_conn(&node_id)?.disable();

    let db = state.db().clone();
    let (affected, change) =
        spawn_blocking(move || db.new_session()?.fork_from(&node_id, &operator)).await??;
    state.pubsub().publish_change(change);

    Ok(Json(Forked { affected }))
}
