use std::{sync::Arc, time::Instant};

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use cotoami_node::prelude::*;
use futures::Stream;
use googletest::prelude::*;
use tempfile::tempdir;
use tokio::sync::oneshot::Sender;

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

/////////////////////////////////////////////////////////////////////////////
// Local node as a service
/////////////////////////////////////////////////////////////////////////////

#[tokio::test]
async fn local_node() -> Result<()> {
    let config = new_node_config()?;
    let state = NodeState::new(config).await?;
    let mut ds = state.db().new_session()?;
    let changes = state.pubsub().changes().subscribe(None::<()>);

    test_node_service(&state, &mut ds, changes).await
}

/////////////////////////////////////////////////////////////////////////////
// WebSocket server as a service
/////////////////////////////////////////////////////////////////////////////

#[tokio::test]
async fn websocket_server() -> Result<()> {
    // Client node
    let client_state = new_client_node_state().await?;

    // Server node
    let (server_state, _shutdown) =
        launch_server_node(client_state.try_get_local_node_id()?).await?;
    let server_id = server_state.try_get_local_node_id()?;

    // Connect the client to the server
    let server = connect_to_server(&client_state, NodeRole::Child).await?;
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
        .subscribe(Some(server_state.try_get_local_node_id()?));
    let _ = test_node_service(parent_service.as_ref(), &mut server_ds, remote_changes).await?;

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

async fn launch_server_node(owner_remote_node_id: Id<Node>) -> Result<(NodeState, Sender<()>)> {
    let mut node_config = new_node_config()?;
    node_config.owner_remote_node_id = Some(owner_remote_node_id);
    node_config.owner_remote_node_password = Some("server-password".into());
    let server_state = NodeState::new(node_config).await?;
    let (_, shutdown) =
        cotoami_node::launch_server(ServerConfig::default(), server_state.clone()).await?;
    Ok((server_state, shutdown))
}

async fn connect_to_server(client_state: &NodeState, role: NodeRole) -> Result<Server> {
    let mut request = Command::AddServer(LogIntoServer {
        url_prefix: Some("http://localhost:5103".into()),
        password: Some("server-password".into()),
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
