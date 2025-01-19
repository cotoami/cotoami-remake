use std::time::Instant;

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use cotoami_node::prelude::*;
use futures::stream::StreamExt;
use googletest::prelude::*;
use test_log::test;

pub mod common;

async fn test_client_connection_management(server_port: u16, enable_websocket: bool) -> Result<()> {
    // Client node
    let client_state = common::new_client_node_state("client").await?;
    let client_id = client_state.try_get_local_node_id()?;

    // Server node
    let (server_state, shutdown) = common::launch_server_node(
        "server",
        server_port,
        enable_websocket,
        AddClient::new(client_id, "server-password", NodeRole::Child),
    )
    .await?;
    let server_id = server_state.try_get_local_node_id()?;
    let mut server_events = server_state.pubsub().events().subscribe(None::<()>);

    // Connect the client to the server
    let server = common::connect_to_server(
        &client_state,
        format!("http://localhost:{server_port}"),
        "server-password",
        NodeRole::Child,
    )
    .await?;
    assert_that!(server.server.node_id, eq(server_id));

    // Assert: the client connection has been registered.
    let active_client = get_active_client(&server_state, &client_id).await?;
    assert_that!(
        active_client,
        pat!(ActiveClient {
            node_id: eq(&client_id),
            remote_addr: eq("127.0.0.1")
        })
    );
    assert_that!(server_state.client_conns().active_clients().len(), eq(1));
    assert_that!(
        server_events.next().await,
        some(eq(&LocalNodeEvent::ClientConnected(active_client)))
    );
    assert!(client_state.server_conns().contains(&server_id));

    // Assert: manual disconnection works.
    server_state.client_conns().disconnect(&client_id);
    assert_that!(server_state.client_conns().active_clients().len(), eq(0));
    assert_that!(
        server_events.next().await,
        some(pat!(LocalNodeEvent::ClientDisconnected {
            node_id: eq(&client_id),
            error: none()
        }))
    );

    shutdown.send(()).ok();

    Ok(())
}

#[test(tokio::test)]
async fn client_connection_on_websocket_server() -> Result<()> {
    test_client_connection_management(5103, true).await
}

#[test(tokio::test)]
async fn client_connection_on_http_server() -> Result<()> {
    test_client_connection_management(5104, false).await
}

async fn get_active_client(node_state: &NodeState, client_id: &Id<Node>) -> Result<ActiveClient> {
    let mut client = node_state.client_conns().active_client(client_id);
    let start = Instant::now();
    while client.is_none() && start.elapsed().as_secs() < 10 {
        tokio::task::yield_now().await;
        client = node_state.client_conns().active_client(client_id);
    }
    client.ok_or(anyhow!("Could not get the active client: {client_id}"))
}
