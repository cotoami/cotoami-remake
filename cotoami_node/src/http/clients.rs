use axum::{
    extract::{Query, State},
    http::StatusCode,
    Extension, Form, Json,
};
use cotoami_db::prelude::*;
use rand::{distributions::Alphanumeric, thread_rng, Rng};
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    api::error::{ApiError, IntoApiResult},
    http::Pagination,
    AppState,
};

const DEFAULT_PAGE_SIZE: i64 = 30;

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/clients
/////////////////////////////////////////////////////////////////////////////

pub(crate) async fn recent_client_nodes(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Query(pagination): Query<Pagination>,
) -> Result<Json<Paginated<Node>>, ApiError> {
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
pub(crate) struct AddClientNode {
    #[validate(required)]
    id: Option<Id<Node>>,
    as_owner: Option<bool>,
    can_edit_links: Option<bool>,
    as_parent: Option<bool>,
}

#[derive(serde::Serialize)]
pub(crate) struct ClientNodeAdded {
    /// Generated password
    password: String,
}

pub(crate) async fn add_client_node(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Form(form): Form<AddClientNode>,
) -> Result<(StatusCode, Json<ClientNodeAdded>), ApiError> {
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
