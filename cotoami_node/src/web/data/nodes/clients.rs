use std::sync::Arc;

use axum::{
    extract::{Query, State},
    http::StatusCode,
    routing::get,
    Extension, Form, Router, TypedHeader,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{AddClientNode, ClientNodeAdded, Pagination},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new().route("/", get(recent_client_nodes).post(add_client_node))
}

const DEFAULT_PAGE_SIZE: i64 = 30;

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/clients
/////////////////////////////////////////////////////////////////////////////

async fn recent_client_nodes(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<Paginated<Node>>, ServiceError> {
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
        Ok(Content(nodes, accept))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/data/nodes/clients
/////////////////////////////////////////////////////////////////////////////

async fn add_client_node(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Form(form): Form<AddClientNode>,
) -> Result<(StatusCode, Content<ClientNodeAdded>), ServiceError> {
    state
        .add_client_node(form, Arc::new(operator))
        .await
        .map(|added| (StatusCode::CREATED, Content(added, accept)))
}
