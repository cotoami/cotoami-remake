use anyhow::Result;
use axum::{
    extract::{Path, State},
    http::StatusCode,
    routing::{get, put},
    Extension, Form, Router, TypedHeader,
};
use cotoami_db::prelude::*;
use derive_new::new;
use tokio::task::spawn_blocking;
use tracing::{debug, info};
use validator::Validate;

use crate::{
    client::{HttpClient, SseClient},
    service::{
        error::IntoServiceResult,
        models::{CreateClientNodeSession, NotConnected},
        RemoteNodeServiceExt, ServiceError,
    },
    state::{NodeState, ServerConnection},
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(all_server_nodes).post(add_server_node))
        .route("/:node_id", put(update_server_node))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/servers
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize, new)]
struct Server {
    node: Node,
    url_prefix: String,
    is_parent: bool,
    not_connected: Option<NotConnected>,
}

async fn all_server_nodes(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
) -> Result<Content<Vec<Server>>, ServiceError> {
    spawn_blocking(move || {
        let conns = state.read_server_conns();
        let mut db = state.db().new_session()?;
        let nodes = db
            .all_server_nodes(&operator)?
            .into_iter()
            .map(|(server, node)| {
                let conn = conns.get(&server.node_id).unwrap_or_else(|| unreachable!());
                Server::new(
                    node,
                    server.url_prefix,
                    state.is_parent(&server.node_id),
                    conn.not_connected(),
                )
            })
            .collect();
        Ok(Content(nodes, accept))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/nodes/servers
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct AddServerNode {
    #[validate(required, url)]
    url_prefix: Option<String>,

    #[validate(required)]
    password: Option<String>,

    as_child: Option<bool>,

    // Settings for the server as a parent (`as_child` = false)
    //
    /// Set true if you want to turn the local node into a replica of the parent node,
    /// which means the root cotonoma will be changed to that of the parent.
    replicate: Option<bool>,
}

async fn add_server_node(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Form(form): Form<AddServerNode>,
) -> Result<(StatusCode, Content<Server>), ServiceError> {
    if let Err(errors) = form.validate() {
        return ("nodes/server", errors).into_result();
    }

    // Inputs
    let url_prefix = form.url_prefix.unwrap_or_else(|| unreachable!());
    let password = form.password.unwrap_or_else(|| unreachable!());
    let server_as_child = form.as_child.unwrap_or(false);

    // Get the local node
    let db = state.db().clone();
    let local_node = spawn_blocking(move || db.new_session()?.local_node()).await??;

    // Attempt to log into the server node
    let mut http_client = HttpClient::new(url_prefix)?;
    let client_session = http_client
        .create_client_node_session(CreateClientNodeSession {
            password: password.clone(),
            new_password: None, // TODO
            client: local_node,
            as_parent: Some(server_as_child),
        })
        .await?;
    info!("Successfully logged in to {}", http_client.url_prefix());
    let server_id = client_session.server.uuid;

    // Register the server node
    let (server_node, server_db_role) = spawn_blocking({
        let state = state.clone();
        let operator = operator.clone();
        let url_prefix = http_client.url_prefix().to_string();
        move || {
            let mut ds = state.db().new_session()?;

            // Import the server node data, which is required for registering a [ServerNode]
            if let Some((_, changelog)) = ds.import_node(&client_session.server)? {
                state.pubsub().publish_change(changelog);
            }

            // Database role
            let server_db_role = if server_as_child {
                NewDatabaseRole::Child {
                    as_owner: false,
                    can_edit_links: false,
                }
            } else {
                NewDatabaseRole::Parent
            };

            // Register a [ServerNode] and save the password into it
            let owner_password = state.config().owner_password();
            let (_, server_db_role) =
                ds.register_server_node(&server_id, &url_prefix, server_db_role, &operator)?;
            ds.save_server_password(&server_id, &password, owner_password, &operator)?;

            // Get the imported node data
            let node = ds.node(&server_id)?.unwrap_or_else(|| unreachable!());
            Ok::<_, ServiceError>((node, server_db_role))
        }
    })
    .await??;
    info!("ServerNode [{}] registered.", server_node.name);

    // Create a SSE client
    let mut sse_client = SseClient::new(server_id, http_client.clone(), state.clone()).await?;
    sse_client.connect();

    // Store the server connection
    let server_conn = ServerConnection::new_sse(sse_client);
    let server = Server::new(
        server_node,
        http_client.url_prefix().to_string(),
        matches!(server_db_role, DatabaseRole::Parent(_)),
        server_conn.not_connected(),
    );
    state.put_server_conn(&server_id, server_conn);

    Ok((StatusCode::CREATED, Content(server, accept)))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/nodes/servers/:node_id
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct UpdateServerNode {
    disabled: Option<bool>,
    // TODO: url_prefix
}

async fn update_server_node(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    Path(node_id): Path<Id<Node>>,
    Form(form): Form<UpdateServerNode>,
) -> Result<StatusCode, ServiceError> {
    if let Err(errors) = form.validate() {
        return ("nodes/server", errors).into_result();
    }
    if !state.contains_server(&node_id) {
        return Err(ServiceError::NotFound);
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
        state.write_server_conn(&server_id)?.disconnect();

    // Or reconnect to the server
    } else {
        debug!("Enabling the connection to {}", server_id);
        state.write_server_conn(&server_id)?.reconnect();
    }

    Ok(())
}
