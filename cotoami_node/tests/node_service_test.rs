use std::{sync::Arc, time::Instant};

use anyhow::Result;
use cotoami_db::prelude::*;
use cotoami_node::prelude::*;
use futures::Stream;
use googletest::prelude::*;
use tempfile::tempdir;

async fn test_node_service<S, C>(
    service: &S,
    ds: &mut DatabaseSession<'_>,
    changes: C,
) -> Result<()>
where
    S: NodeService + ?Sized,
    C: Stream<Item = ChangelogEntry>,
{
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
        pat!(Node {
            name: eq("Cotoami")
        })
    );

    Ok(())
}

fn default_config() -> Result<NodeConfig> {
    let db_dir = tempdir()?;
    Ok(NodeConfig::new_standalone(
        Some(db_dir.path().to_string_lossy().into()),
        Some("Cotoami".into()),
    ))
}

#[tokio::test]
async fn local_node() -> Result<()> {
    let config = default_config()?;
    let state = NodeState::new(config).await?;
    let mut ds = state.db().new_session()?;
    let changes = state.pubsub().changes().subscribe(None::<()>);

    test_node_service(&state, &mut ds, changes).await
}

#[tokio::test]
async fn websocket_server() -> Result<()> {
    // Client node
    let mut client_config = default_config()?;
    client_config.owner_password = Some("master-password".into());
    let client_state = NodeState::new(client_config).await?;
    let client_opr = Arc::new(client_state.local_node_as_operator()?);

    // Server node
    let mut server_config = default_config()?;
    server_config.owner_remote_node_id = Some(client_state.try_get_local_node_id()?);
    server_config.owner_remote_node_password = Some("server-password".into());
    let server_state = NodeState::new(server_config).await?;
    let server_id = server_state.try_get_local_node_id()?;
    let (_handle, _shutdown_trigger) =
        cotoami_node::launch_server(ServerConfig::default(), server_state.clone()).await?;

    // Connect the client to the server
    let mut request = Command::AddServer(LogIntoServer {
        url_prefix: Some("http://localhost:5103".into()),
        password: Some("server-password".into()),
        new_password: None,
        client_role: Some(NodeRole::Child),
    })
    .into_request();
    request.set_from(client_opr.clone());
    let response = client_state.call(request).await?;
    let server = response.content::<Server>()?;
    assert_that!(server.server.node_id, eq(server_id));

    // Test the server service via the client node
    let mut parent_service = client_state.parent_services().get(&server_id);
    let start = Instant::now();
    while parent_service.is_none() && start.elapsed().as_secs() < 10 {
        tokio::task::yield_now().await;
        parent_service = client_state.parent_services().get(&server_id);
    }
    let mut server_ds = server_state.db().new_session()?;
    let remote_changes = client_state
        .pubsub()
        .remote_changes()
        .subscribe(Some(server_state.try_get_local_node_id()?));
    let _ = test_node_service(
        parent_service.unwrap().as_ref(),
        &mut server_ds,
        remote_changes,
    )
    .await?;

    Ok(())
}
