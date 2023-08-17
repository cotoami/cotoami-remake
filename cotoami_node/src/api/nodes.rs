use axum::{
    extract::{Query, State},
    http::StatusCode,
    middleware,
    routing::get,
    Extension, Form, Json, Router,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    api::Pagination,
    error::{ApiError, IntoApiResult, RequestError},
    AppState,
};

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/local", get(get_local_node))
        .route("/children", get(recent_child_nodes).post(add_child_node))
        .layer(middleware::from_fn(super::require_session))
}

const DEFAULT_PAGE_SIZE: i64 = 30;

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
// GET /api/nodes/children
/////////////////////////////////////////////////////////////////////////////

async fn recent_child_nodes(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Query(pagination): Query<Pagination>,
) -> Result<Json<Paginated<Node>>, ApiError> {
    if let Err(errors) = pagination.validate() {
        return ("nodes/children", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        let nodes = db
            .recent_child_nodes(
                pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
                pagination.page,
                &operator,
            )?
            .map(|(_, node)| node)
            .into();
        Ok(Json(nodes))
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
