use axum::extract::{Path, Query, State};
use cotoami_db::prelude::*;

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
