use std::sync::Arc;

use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    routing::{get, put},
    Extension, Form, Router,
};
use axum_extra::TypedHeader;
use cotoami_db::prelude::*;

use crate::{
    service::{
        models::{ClientNodeSession, EditServer, LogIntoServer, Server},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(all_servers).post(add_server))
        .route("/try", get(log_into_server))
        .route("/:node_id", put(edit_server))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/servers
/////////////////////////////////////////////////////////////////////////////

async fn all_servers(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
) -> Result<Content<Vec<Server>>, ServiceError> {
    state
        .all_servers(Arc::new(operator))
        .await
        .map(|servers| Content(servers, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/servers/try
/////////////////////////////////////////////////////////////////////////////

async fn log_into_server(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(input): Query<LogIntoServer>,
) -> Result<(StatusCode, Content<ClientNodeSession>), ServiceError> {
    state
        .log_into_server(input)
        .await
        .map(|(session, _)| (StatusCode::CREATED, Content(session, accept)))
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/data/nodes/servers
/////////////////////////////////////////////////////////////////////////////

async fn add_server(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Form(form): Form<LogIntoServer>,
) -> Result<(StatusCode, Content<Server>), ServiceError> {
    state
        .add_server(form, Arc::new(operator))
        .await
        .map(|server| (StatusCode::CREATED, Content(server, accept)))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/nodes/servers/:node_id
/////////////////////////////////////////////////////////////////////////////

async fn edit_server(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    Path(node_id): Path<Id<Node>>,
    Form(form): Form<EditServer>,
) -> Result<StatusCode, ServiceError> {
    state
        .edit_server(node_id, form, Arc::new(operator))
        .await
        .map(|_| StatusCode::OK)
}
