use std::sync::Arc;

use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    routing::{get, put},
    Extension, Form, Router, TypedHeader,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::debug;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{ClientNodeSession, ConnectServerNode, Server},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(all_server_nodes).post(add_server_node))
        .route("/try", get(connect_server_node))
        .route("/:node_id", put(update_server_node))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/servers
/////////////////////////////////////////////////////////////////////////////

async fn all_server_nodes(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
) -> Result<Content<Vec<Server>>, ServiceError> {
    state
        .all_server_nodes(Arc::new(operator))
        .await
        .map(|servers| Content(servers, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/servers/try
/////////////////////////////////////////////////////////////////////////////

async fn connect_server_node(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(input): Query<ConnectServerNode>,
) -> Result<(StatusCode, Content<ClientNodeSession>), ServiceError> {
    state
        .connect_server_node(input)
        .await
        .map(|(session, _)| (StatusCode::CREATED, Content(session, accept)))
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/nodes/servers
/////////////////////////////////////////////////////////////////////////////

async fn add_server_node(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Form(form): Form<ConnectServerNode>,
) -> Result<(StatusCode, Content<Server>), ServiceError> {
    state
        .add_server_node(form, Arc::new(operator))
        .await
        .map(|server| (StatusCode::CREATED, Content(server, accept)))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/nodes/servers/:node_id
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct UpdateServerNode {
    disabled: Option<bool>,
    // TODO: url_prefix
}

#[axum_macros::debug_handler]
async fn update_server_node(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    Path(node_id): Path<Id<Node>>,
    Form(form): Form<UpdateServerNode>,
) -> Result<StatusCode, ServiceError> {
    if let Err(errors) = form.validate() {
        return ("nodes/server", errors).into_result();
    }
    if !state.server_conns().contains(&node_id) {
        return Err(ServiceError::NotFound(Some(format!(
            "Server node [{node_id}] not found."
        ))));
    }
    if let Some(disabled) = form.disabled {
        set_server_disabled(node_id, disabled, &state, operator).await?;
    }
    Ok(StatusCode::OK)
}

async fn set_server_disabled(
    server_id: Id<Node>,
    disabled: bool,
    state: &NodeState,
    operator: Operator,
) -> Result<()> {
    // Set `disabled` to true/false
    spawn_blocking({
        let db = state.db().clone();
        move || {
            let ds = db.new_session()?;
            ds.set_network_disabled(&server_id, disabled, &operator)?;
            Ok::<_, anyhow::Error>(())
        }
    })
    .await??;

    // Disconnect from the server
    if disabled {
        debug!("Disabling the connection to: {}", server_id);
        state.server_conns().try_get(&server_id)?.disable();

    // Or reconnect to the server
    } else {
        debug!("Enabling the connection to {}", server_id);
        state.server_conns().try_get(&server_id)?.connect().await;
    }

    Ok(())
}
