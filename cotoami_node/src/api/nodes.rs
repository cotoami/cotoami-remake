use axum::{
    extract::State,
    http::StatusCode,
    middleware,
    routing::{get, post},
    Extension, Form, Json, Router,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    error::{ApiError, IntoApiResult, RequestError},
    AppState,
};

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/local", get(get_local_node))
        .route("/children", post(add_child_node))
        .layer(middleware::from_fn(super::require_session))
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
// POST /api/nodes/children
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct AddChildNode {
    #[validate(required)]
    id: Option<Id<Node>>,

    #[validate(required)]
    password: Option<String>,

    as_owner: Option<bool>,

    can_edit_links: Option<bool>,
}

async fn add_child_node(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Form(form): Form<AddChildNode>,
) -> Result<StatusCode, ApiError> {
    if let Err(errors) = form.validate() {
        return ("nodes/child", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        db.add_child_node(
            form.id.unwrap(),        // validated to be Some
            &form.password.unwrap(), // validated to be Some
            form.as_owner.unwrap_or(false),
            form.can_edit_links.unwrap_or(false),
            &operator,
        )?;
        Ok(StatusCode::CREATED)
    })
    .await?
}
