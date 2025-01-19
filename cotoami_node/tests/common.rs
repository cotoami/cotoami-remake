use std::sync::Arc;

use anyhow::Result;
use cotoami_node::prelude::*;
use tempfile::tempdir;
use tokio::sync::oneshot::Sender;

pub fn new_node_config(name: impl Into<String>) -> Result<NodeConfig> {
    let db_dir = tempdir()?;
    Ok(NodeConfig::new_standalone(
        Some(db_dir.path().to_string_lossy().into()),
        Some(name.into()),
    ))
}

pub async fn new_client_node_state(name: impl Into<String>) -> Result<NodeState> {
    let mut node_config = new_node_config(name)?;
    node_config.owner_password = Some("master-password".into());
    NodeState::new(node_config).await
}

pub async fn launch_server_node(
    name: impl Into<String>,
    port: u16,
    enable_websocket: bool,
    add_client: AddClient,
) -> Result<(NodeState, Sender<()>)> {
    let server_state = NodeState::new(new_node_config(name)?).await?;
    let opr = server_state.local_node_as_operator()?;
    server_state
        .add_client(add_client, Arc::new(opr))
        .await
        .map_err(BackendServiceError)?;

    let mut server_config = ServerConfig::default();
    server_config.port = port;
    server_config.url_port = Some(port);
    server_config.enable_websocket = enable_websocket;
    let (_, shutdown) = cotoami_node::launch_server(server_config, server_state.clone()).await?;

    Ok((server_state, shutdown))
}

pub async fn connect_to_server(
    client_state: &NodeState,
    url_prefix: impl Into<String>,
    password: impl Into<String>,
    role: NodeRole,
) -> Result<Server> {
    let mut request = Command::AddServer(LogIntoServer {
        url_prefix: Some(url_prefix.into()),
        password: Some(password.into()),
        new_password: None,
        client_role: Some(role),
    })
    .into_request();
    request.set_from(Arc::new(client_state.local_node_as_operator()?));
    let response = client_state.call(request).await?;
    response.content::<Server>()
}
