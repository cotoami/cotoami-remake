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
    service::{error::IntoServiceResult, Pagination, ServiceError},
    NodeState,
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_client_nodes).post(add_client_node))
        .layer(middleware::from_fn(crate::web::require_session))
}

const DEFAULT_PAGE_SIZE: i64 = 30;

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/clients
/////////////////////////////////////////////////////////////////////////////

async fn recent_client_nodes(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    Query(pagination): Query<Pagination>,
) -> Result<Json<Paginated<Node>>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("nodes/clients", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db().new_session()?;
        let nodes = db
            .recent_client_nodes(
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
// POST /api/nodes/clients
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct AddClientNode {
    #[validate(required)]
    id: Option<Id<Node>>,
    as_owner: Option<bool>,
    can_edit_links: Option<bool>,
    as_parent: Option<bool>,
}

#[derive(serde::Serialize)]
struct ClientNodeAdded {
    /// Generated password
    password: String,
}

async fn add_client_node(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    Form(form): Form<AddClientNode>,
) -> Result<(StatusCode, Json<ClientNodeAdded>), ServiceError> {
    if let Err(errors) = form.validate() {
        return ("nodes/client", errors).into_result();
    }
    spawn_blocking(move || {
        let db = state.db().new_session()?;
        let password = generate_password();
        let database_role = if form.as_parent.unwrap_or(false) {
            NewDatabaseRole::Parent
        } else {
            NewDatabaseRole::Child {
                as_owner: form.as_owner.unwrap_or(false),
                can_edit_links: form.can_edit_links.unwrap_or(false),
            }
        };
        db.register_client_node(
            form.id.unwrap_or_else(|| unreachable!()),
            &password,
            database_role,
            &operator,
        )?;
        let response_body = ClientNodeAdded { password };
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
