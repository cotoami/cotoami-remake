use super::ClientError;
use super::WebError;
use crate::AppState;
use axum::extract::State;
use axum::routing::get;
use axum::{Json, Router};
use cotoami_db::prelude::*;
use derive_new::new;
use tokio::task::spawn_blocking;

pub(super) fn routes() -> Router<AppState> {
    Router::new().route("/local", get(local_get))
}

#[derive(new, serde::Serialize)]
struct LocalNode {
    node_id: Id<Node>,
    name: String,
}

async fn local_get(State(state): State<AppState>) -> Result<Json<LocalNode>, WebError> {
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        if let Some((local_node, node)) = db.get_local_node()? {
            Ok(Json(LocalNode::new(local_node.node_id, node.name)))
        } else {
            ClientError::new(
                "local-node-not-yet-created".into(),
                Vec::new(),
                "Local node has not yet been created.".into(),
            )
            .into_result()
        }
    })
    .await?
}
