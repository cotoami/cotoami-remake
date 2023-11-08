use axum::{
    extract::{Query, State},
    http::StatusCode,
    middleware,
    routing::get,
    Extension, Form, Json, Router,
};
use cotoami_db::prelude::*;
use rand::{distributions::Alphanumeric, thread_rng, Rng};
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    api::error::{ApiError, IntoApiResult},
    http::{require_session, Pagination},
    AppState,
};

pub(crate) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", get(recent_child_nodes).post(add_child_node))
        .layer(middleware::from_fn(require_session))
}

const DEFAULT_PAGE_SIZE: i64 = 30;

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
        let mut db = state.db.new_session()?;
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

    as_owner: Option<bool>,

    can_edit_links: Option<bool>,
}

#[derive(serde::Serialize)]
struct ChildNodeAdded {
    /// Generated password
    password: String,
}

async fn add_child_node(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Form(form): Form<AddChildNode>,
) -> Result<(StatusCode, Json<ChildNodeAdded>), ApiError> {
    if let Err(errors) = form.validate() {
        return ("nodes/child", errors).into_result();
    }
    spawn_blocking(move || {
        let db = state.db.new_session()?;
        let password = generate_password();
        db.register_child_node(
            form.id.unwrap_or_else(|| unreachable!()),
            &password,
            form.as_owner.unwrap_or(false),
            form.can_edit_links.unwrap_or(false),
            &operator,
        )?;
        let response_body = ChildNodeAdded { password };
        Ok((StatusCode::CREATED, Json(response_body)))
    })
    .await?
}

fn generate_password() -> String {
    // https://rust-lang-nursery.github.io/rust-cookbook/algorithms/randomness.html#create-random-passwords-from-a-set-of-alphanumeric-characters
    thread_rng()
        .sample_iter(&Alphanumeric)
        .take(32)
        .map(char::from)
        .collect()
}
