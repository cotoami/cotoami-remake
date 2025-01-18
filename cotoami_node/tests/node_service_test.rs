use std::{sync::Arc, time::Instant};

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use cotoami_node::prelude::*;
use futures::Stream;
use googletest::prelude::*;
use tempfile::tempdir;
use test_log::test;
use tokio::sync::oneshot::Sender;

/// Test a [NodeService] by sending [Command]s.
/// Various service backends are defined in each test function below.
async fn test_node_service<S, C>(
    service: &S,
    ds: &mut DatabaseSession<'_>,
    changes: C,
) -> Result<()>
where
    S: NodeService + ?Sized,
    C: Stream<Item = ChangelogEntry>,
{
    let service_node = ds.local_node()?;

    /////////////////////////////////////////////////////////////////////////////
    // Command: LocalNode
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::LocalNode.into_request();
    let request_id = *request.id();
    let response = service.call(request).await?;
    assert_that!(response.id(), eq(&request_id));

    let node = response.content::<Node>()?;
    assert_that!(
        node,
        eq(&Node {
            rowid: 0, // skip_deserializing
            ..service_node.clone()
        })
    );

    Ok(())
}

#[test(tokio::test)]
async fn service_based_on_local_node() -> Result<()> {
    let config = new_node_config()?;
    let state = NodeState::new(config).await?;
    let mut ds = state.db().new_session()?;
    let changes = state.pubsub().changes().subscribe(None::<()>);

    test_node_service(&state, &mut ds, changes).await
}

#[test(tokio::test)]
async fn service_based_on_websocket_server() -> Result<()> {
    // Client node
    let client_state = new_client_node_state().await?;
    let client_id = client_state.try_get_local_node_id()?;

    // Server node
    let (server_state, shutdown) = launch_server_node(
        5103,
        true,
        AddClient::new(client_id, "server-password", NodeRole::Child),
    )
    .await?;
    let server_id = server_state.try_get_local_node_id()?;

    // Connect the client to the server
    let server = connect_to_server(
        &client_state,
        "http://localhost:5103",
        "server-password",
        NodeRole::Child,
    )
    .await?;
    assert_that!(server.server.node_id, eq(server_id));

    // Test the server service via the client node
    let parent_service = get_parent_service(&client_state, &server_id).await?;
    assert_that!(
        parent_service.description(),
        eq("WebSocket server-as-parent: ws://localhost:5103/api/ws")
    );
    let mut server_ds = server_state.db().new_session()?;
    let remote_changes = client_state
        .pubsub()
        .remote_changes()
        .subscribe(Some(server_id));
    let _ = test_node_service(parent_service.as_ref(), &mut server_ds, remote_changes).await?;

    shutdown.send(()).ok();

    Ok(())
}

#[test(tokio::test)]
async fn service_based_on_websocket_client() -> Result<()> {
    // Client node
    let client_state = new_client_node_state().await?;
    let client_id = client_state.try_get_local_node_id()?;

    // Server node
    let (server_state, shutdown) = launch_server_node(
        5104,
        true,
        AddClient::new(client_id, "server-password", NodeRole::Parent),
    )
    .await?;
    let server_id = server_state.try_get_local_node_id()?;

    // Connect the client to the server
    let server = connect_to_server(
        &client_state,
        "http://localhost:5104",
        "server-password",
        NodeRole::Parent,
    )
    .await?;
    assert_that!(server.server.node_id, eq(server_id));

    // Test the server service via the client node
    let parent_service = get_parent_service(&server_state, &client_id).await?;
    assert_that!(
        parent_service.description(),
        eq(&format!("WebSocket client-as-parent: {}", client_id))
    );
    let mut client_ds = client_state.db().new_session()?;
    let remote_changes = server_state
        .pubsub()
        .remote_changes()
        .subscribe(Some(client_id));
    let _ = test_node_service(parent_service.as_ref(), &mut client_ds, remote_changes).await?;

    shutdown.send(()).ok();

    Ok(())
}

#[test(tokio::test)]
async fn service_based_on_http_server() -> Result<()> {
    // Client node
    let client_state = new_client_node_state().await?;
    let client_id = client_state.try_get_local_node_id()?;

    // Server node
    let (server_state, shutdown) = launch_server_node(
        5105,
        false,
        AddClient::new(client_id, "server-password", NodeRole::Child),
    )
    .await?;
    let server_id = server_state.try_get_local_node_id()?;

    // Connect the client to the server
    let server = connect_to_server(
        &client_state,
        "http://localhost:5105",
        "server-password",
        NodeRole::Child,
    )
    .await?;
    assert_that!(server.server.node_id, eq(server_id));

    // Test the server service via the client node
    let parent_service = get_parent_service(&client_state, &server_id).await?;
    assert_that!(
        parent_service.description(),
        eq("HTTP server-as-parent: http://localhost:5105/")
    );
    let mut server_ds = server_state.db().new_session()?;
    let remote_changes = client_state
        .pubsub()
        .remote_changes()
        .subscribe(Some(server_id));
    let _ = test_node_service(parent_service.as_ref(), &mut server_ds, remote_changes).await?;

    shutdown.send(()).ok();

    Ok(())
}

/////////////////////////////////////////////////////////////////////////////
// Utils
/////////////////////////////////////////////////////////////////////////////

fn new_node_config() -> Result<NodeConfig> {
    let db_dir = tempdir()?;
    Ok(NodeConfig::new_standalone(
        Some(db_dir.path().to_string_lossy().into()),
        Some("Cotoami".into()),
    ))
}

async fn new_client_node_state() -> Result<NodeState> {
    let mut node_config = new_node_config()?;
    node_config.owner_password = Some("master-password".into());
    NodeState::new(node_config).await
}

async fn launch_server_node(
    port: u16,
    enable_websocket: bool,
    add_client: AddClient,
) -> Result<(NodeState, Sender<()>)> {
    let server_state = NodeState::new(new_node_config()?).await?;
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

async fn connect_to_server(
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

async fn get_parent_service(
    client_state: &NodeState,
    parent_id: &Id<Node>,
) -> Result<Box<dyn NodeService>> {
    let mut parent_service = client_state.parent_services().get(parent_id);
    let start = Instant::now();
    while parent_service.is_none() && start.elapsed().as_secs() < 10 {
        tokio::task::yield_now().await;
        parent_service = client_state.parent_services().get(parent_id);
    }
    parent_service.ok_or(anyhow!("Could not get the parent service: {parent_id}"))
}
