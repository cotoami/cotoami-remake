use axum::{extract::State, routing::get, Form, Json, Router};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    api::{ApiError, IntoApiResult, RequestError},
    AppState,
};

pub(super) fn routes() -> Router<AppState> {
    Router::new().route("/local", get(get_local_node).put(init_local_node))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/local
/////////////////////////////////////////////////////////////////////////////

async fn get_local_node(State(state): State<AppState>) -> Result<Json<Node>, ApiError> {
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        if let Some((_, node)) = db.get_local_node()? {
            Ok(Json(node))
        } else {
            RequestError::new("local-node-not-yet-created").into_result()
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
    Form(form): Form<InitNode>,
) -> Result<Json<Node>, ApiError> {
    if let Err(errors) = form.validate() {
        return ("local-node", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        if db.get_local_node()?.is_some() {
            RequestError::new("local-node-already-exists").into_result()
        } else {
            let ((_, node), _) = if let Some(name) = form.name {
                db.init_as_node(&name, state.config.owner_password.as_deref())?
            } else {
                db.init_as_empty_node(state.config.owner_password.as_deref())?
            };
            Ok(Json(node))
        }
    })
    .await?
}
