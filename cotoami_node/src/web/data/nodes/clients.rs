use std::sync::Arc;

use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    routing::get,
    Extension, Form, Router, TypedHeader,
};
use cotoami_db::prelude::*;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{AddClient, ClientAdded, Pagination, UpdateClient},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_clients).post(add_client))
        .route("/:node_id", get(client).put(update_client_node))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/clients
/////////////////////////////////////////////////////////////////////////////

async fn recent_clients(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<Page<ClientNode>>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return errors.into_result();
    }
    state
        .recent_clients(pagination, Arc::new(operator))
        .await
        .map(|clients| Content(clients, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/clients/:node_id
/////////////////////////////////////////////////////////////////////////////

async fn client(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(node_id): Path<Id<Node>>,
) -> Result<Content<ClientNode>, ServiceError> {
    state
        .client_node(node_id, Arc::new(operator))
        .await
        .map(|client| Content(client, accept))
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/data/nodes/clients
/////////////////////////////////////////////////////////////////////////////

async fn add_client(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Form(form): Form<AddClient>,
) -> Result<(StatusCode, Content<ClientAdded>), ServiceError> {
    state
        .add_client(form, Arc::new(operator))
        .await
        .map(|added| (StatusCode::CREATED, Content(added, accept)))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/nodes/clients/:node_id
/////////////////////////////////////////////////////////////////////////////

async fn update_client_node(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    Path(node_id): Path<Id<Node>>,
    Form(form): Form<UpdateClient>,
) -> Result<StatusCode, ServiceError> {
    state
        .update_client(node_id, form, Arc::new(operator))
        .await
        .map(|_| StatusCode::OK)
}
