use anyhow::Result;
use axum::{
    extract::{Path, State},
    http::StatusCode,
    Extension, Form, Json,
};
use cotoami_db::prelude::*;
use derive_new::new;
use tokio::task::spawn_blocking;
use tracing::{debug, info};
use validator::Validate;

use crate::{
    api::error::{ApiError, IntoApiResult},
    client::{HttpClient, SseClient},
    service::RemoteNodeServiceExt,
    state::{
        conn::{NotConnected, ServerConnection},
        AppState, CreateClientNodeSession,
    },
};

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/servers
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize, new)]
pub(crate) struct Server {
    node: Node,
    not_connected: Option<NotConnected>,
}

pub(crate) async fn all_servers(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
) -> Result<Json<Vec<Server>>, ApiError> {
    spawn_blocking(move || {
        let conns = state.server_conns.read();
        let mut db = state.db.new_session()?;
        let nodes = db
            .all_server_nodes(&operator)?
            .into_iter()
            .map(|(_, node)| {
                let conn = conns.get(&node.uuid).unwrap_or_else(|| unreachable!());
                Server::new(node, conn.not_connected())
            })
            .collect();
        Ok(Json(nodes))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/nodes/servers
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
pub(crate) struct AddServerNode {
    #[validate(required, url)]
    url_prefix: Option<String>,

    #[validate(required)]
    password: Option<String>,

    as_child: Option<bool>,

    /// Set true if you want to turn this node into a replica of the parent node,
    /// which means the root cotonoma will be changed to that of the parent.
    replicate: Option<bool>,
}

pub(crate) async fn add_server_node(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Form(form): Form<AddServerNode>,
) -> Result<(StatusCode, Json<Server>), ApiError> {
    if let Err(errors) = form.validate() {
        return ("nodes/server", errors).into_result();
    }

    // Inputs
    let url_prefix = form.url_prefix.unwrap_or_else(|| unreachable!());
    let password = form.password.unwrap_or_else(|| unreachable!());
    let as_child = form.as_child.unwrap_or(false);

    // Get the local node
    let db = state.db.clone();
    let local_node = spawn_blocking(move || db.new_session()?.local_node()).await??;

    // Attempt to log into the server node
    let mut http_client = HttpClient::new(url_prefix)?;
    let client_session = http_client
        .create_client_node_session(CreateClientNodeSession {
            password: password.clone(),
            new_password: None, // TODO
            client: local_node,
            as_parent: form.as_child, // if server=child, then client=parent
        })
        .await?;
    info!("Successfully logged in to {}", http_client.url_prefix());

    // Register the server node
    let (config, db, pubsub) = (state.config.clone(), state.db.clone(), state.pubsub.clone());
    let op = operator.clone();
    let server_id = client_session.server.uuid;
    let url_prefix = http_client.url_prefix().to_string();
    let (server_node, server_db_role) = spawn_blocking(move || {
        let owner_password = config.owner_password();
        let mut db = db.new_session()?;

        // Import the server node data, which is required for registering a [ServerNode]
        if let Some((_, changelog)) = db.import_node(&client_session.server)? {
            pubsub.publish_change(changelog);
        }

        // Database role
        let server_db_role = if as_child {
            NewDatabaseRole::Child {
                as_owner: false,
                can_edit_links: false,
            }
        } else {
            NewDatabaseRole::Parent
        };

        // Register a [ServerNode] and save the password into it
        let (_, server_db_role) =
            db.register_server_node(&server_id, &url_prefix, server_db_role, &op)?;
        db.save_server_password(&server_id, &password, owner_password, &op)?;

        // Get the imported node data
        let node = db.node(&server_id)?.unwrap_or_else(|| unreachable!());
        Ok::<_, ApiError>((node, server_db_role))
    })
    .await??;
    info!("ServerNode [{}] registered.", server_node.name);

    // Import changes from the parent
    if let DatabaseRole::Parent(parent) = server_db_role {
        http_client
            .import_changes(parent.node_id, &state.db, &state.pubsub.local_change)
            .await?;
        api::parents::after_first_import(
            server_node.clone(),
            form.replicate.unwrap_or(false),
            state.db.clone(),
            state.pubsub.local_change.clone(),
        )
        .await?;
    }

    // Create a SSE client
    let sse_client = SseClient::new(
        server_id,
        http_client.clone(),
        state.db.clone(),
        state.pubsub.local_change.clone(),
    )?;

    // Store the server connection
    let server_conn = ServerConnection::new(client_session.session, http_client, sse_client);
    let server = Server::new(server_node, server_conn.not_connected());
    state.put_server_conn(&server_id, server_conn);

    Ok((StatusCode::CREATED, Json(server)))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/nodes/servers/:node_id
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
pub(crate) struct UpdateServerNode {
    disabled: Option<bool>,
    // TODO: url_prefix
}

pub(crate) async fn update_server_node(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Path(node_id): Path<Id<Node>>,
    Form(form): Form<UpdateServerNode>,
) -> Result<StatusCode, ApiError> {
    if let Err(errors) = form.validate() {
        return ("nodes/server", errors).into_result();
    }
    if !state.contains_server(&node_id) {
        return Err(ApiError::NotFound);
    }
    if let Some(disabled) = form.disabled {
        set_server_disabled(node_id, disabled, &state, operator).await?;
    }
    Ok(StatusCode::OK)
}

async fn set_server_disabled(
    server_id: Id<Node>,
    disabled: bool,
    state: &AppState,
    operator: Operator,
) -> Result<()> {
    // Set `disabled` to true or false
    let db = state.db.clone();
    let (local_node, network_role) = spawn_blocking(move || {
        let mut db = db.new_session()?;
        Ok::<_, anyhow::Error>((
            db.local_node()?,
            db.set_network_disabled(&server_id, disabled, &operator)?,
        ))
    })
    .await??;
    let NetworkRole::Server(server_node) = network_role else { unreachable!() };

    // Disconnect from the server
    if disabled {
        debug!("Stopping the SSE event loop of: {}", server_id);
        state.server_conn(&server_id)?.disable_sse();

    // Or connect to the server again
    } else {
        if state.server_conn(&server_id)?.restart_sse_if_possible() {
            debug!("Restarting the SSE event loop of {}", server_id);
        } else {
            debug!("Creating a new server connection for {}", server_id);
            let server_conn = ServerConnection::connect(
                &server_node,
                local_node,
                state.config.owner_password(),
                &state.db,
                &state.pubsub.local_change,
            )
            .await;
            state.put_server_conn(&server_id, server_conn);
        }
    }

    Ok(())
}
