use super::{ClientError, ClientErrors, WebError};
use crate::AppState;
use axum::extract::State;
use axum::routing::get;
use axum::{Form, Json, Router};
use cotoami_db::prelude::*;
use derive_new::new;
use tokio::task::spawn_blocking;
use validator::Validate;

pub(super) fn routes() -> Router<AppState> {
    Router::new().route("/local", get(get_local_node).put(init_local_node))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/local
/////////////////////////////////////////////////////////////////////////////

#[derive(new, serde::Serialize)]
struct LocalNode {
    node_id: Id<Node>,
    name: String,
}

async fn get_local_node(State(state): State<AppState>) -> Result<Json<LocalNode>, WebError> {
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        if let Some((local_node, node)) = db.get_local_node()? {
            Ok(Json(LocalNode::new(local_node.node_id, node.name)))
        } else {
            ClientError::resource("local-node", "not-yet-created").into_result()
        }
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/nodes/local
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct InitNode {
    #[validate(length(max = "Node::NAME_MAX_LENGTH"))]
    name: Option<String>,
}

async fn init_local_node(
    State(state): State<AppState>,
    Form(init_node): Form<InitNode>,
) -> Result<Json<LocalNode>, WebError> {
    if let Err(errors) = init_node.validate() {
        return ClientErrors::from_validation_errors("local-node", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        if db.get_local_node()?.is_some() {
            ClientError::resource("local-node", "already_exists").into_result()
        } else {
            let ((local_node, node), _) = if let Some(name) = init_node.name {
                db.init_as_node(&name, state.config.owner_password.as_deref())?
            } else {
                db.init_as_empty_node(state.config.owner_password.as_deref())?
            };
            Ok(Json(LocalNode::new(local_node.node_id, node.name)))
        }
    })
    .await?
}
