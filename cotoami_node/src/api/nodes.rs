use axum::{
    extract::{Query, State},
    http::StatusCode,
    middleware,
    routing::get,
    Extension, Form, Json, Router,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::info;
use validator::Validate;

use crate::{
    api::Pagination,
    client::Server,
    error::{ApiError, IntoApiResult, RequestError},
    AppState,
};

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/local", get(get_local_node))
        .route("/parents", get(all_parent_nodes).put(put_parent_node))
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
        if let Some((_, node)) = db.local_node()? {
            Ok(Json(node))
        } else {
            RequestError::new("local-node-not-yet-created").into_result()
        }
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/parents
/////////////////////////////////////////////////////////////////////////////

async fn all_parent_nodes(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
) -> Result<Json<Vec<Node>>, ApiError> {
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        let nodes = db
            .all_parent_nodes(&operator)?
            .into_iter()
            .map(|(_, node)| node)
            .collect();
        Ok(Json(nodes))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/nodes/parents
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct PutParentNode {
    #[validate(required, url)]
    url_prefix: Option<String>,

    #[validate(required)]
    password: Option<String>,
}

async fn put_parent_node(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Form(form): Form<PutParentNode>,
) -> Result<StatusCode, ApiError> {
    if let Err(errors) = form.validate() {
        return ("nodes/parent", errors).into_result();
    }

    // Get the local node
    let db = state.db.clone();
    let (_, node) = spawn_blocking(move || db.create_session()?.local_node())
        .await??
        .unwrap();

    // Attempt to log in to the parent node
    let password = form.password.unwrap();
    let server = Server::new(form.url_prefix.unwrap(), None)?;
    let child_session = server
        .create_child_session(
            password.clone(),
            None, // TODO
            &node,
        )
        .await?;
    info!("Successfully logged in to {}", server.url_prefix());

    // Register the parent node
    let config = state.config.clone();
    let db = state.db.clone();
    let parent_id = child_session.parent.uuid;
    let url_prefix = server.url_prefix().to_string();
    let parent_node = spawn_blocking(move || {
        let owner_password = config.owner_password.as_deref().unwrap();
        let db = db.create_session()?;
        db.import_node(&child_session.parent)?;
        db.put_parent_node(&parent_id, &url_prefix, &operator)?;
        db.save_parent_password(&parent_id, &password, owner_password, &operator)
    })
    .await??;
    info!("Parent node {} saved.", parent_node.node_id);

    // Import the changelog
    server
        .import_changes(state.db.clone(), state.pubsub.clone(), parent_node.node_id)
        .await?;

    // Create an event stream
    let event_loop = server
        .create_event_loop(parent_node.node_id, state.db.clone(), state.pubsub.clone())
        .await?;

    // Store the parent connection
    state.put_parent_conn(&parent_node.node_id, child_session.session, event_loop);

    Ok(StatusCode::CREATED)
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
        let db = state.db.create_session()?;
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
