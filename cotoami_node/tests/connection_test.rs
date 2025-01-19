use std::time::Duration;

use anyhow::Result;
use cotoami_node::prelude::*;
use futures::stream::StreamExt;
use googletest::prelude::*;
use test_log::test;
use tokio::time::timeout;

pub mod common;

async fn test_connecting_nodes(server_port: u16, enable_websocket: bool) -> Result<()> {
    // Client node
    let client_state = common::new_client_node_state("client").await?;
    let client_id = client_state.try_get_local_node_id()?;
    let mut client_events = client_state.pubsub().events().subscribe(None::<()>);

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

    /////////////////////////////////////////////////////////////////////////////
    // When: a connection has been established
    /////////////////////////////////////////////////////////////////////////////

    let server = common::connect_to_server(
        &client_state,
        format!("http://localhost:{server_port}"),
        "server-password",
        NodeRole::Child,
    )
    .await?;
    assert_that!(server.server.node_id, eq(server_id));

    // Server-side
    assert_that!(
        timeout(Duration::from_secs(5), server_events.next()).await?,
        some(pat!(LocalNodeEvent::ClientConnected(pat!(ActiveClient {
            node_id: eq(&client_id),
            remote_addr: eq("127.0.0.1")
        }))))
    );
    assert_that!(
        server_state.client_conns().active_client(&client_id),
        some(pat!(ActiveClient {
            node_id: eq(&client_id),
            remote_addr: eq("127.0.0.1")
        }))
    );
    assert_that!(server_state.client_conns().active_clients().len(), eq(1));

    // Client-side
    assert_that!(
        timeout(Duration::from_secs(5), client_events.next()).await?,
        some(pat!(LocalNodeEvent::ServerStateChanged {
            node_id: eq(&server_id),
            not_connected: none() // It means "connected".
        }))
    );
    assert!(client_state.server_conns().contains(&server_id));

    /////////////////////////////////////////////////////////////////////////////
    // When: manual disconnection by server-side
    /////////////////////////////////////////////////////////////////////////////

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
async fn connecting_to_websocket_server() -> Result<()> {
    // A comment line to avoid https://github.com/rust-lang/rust/issues/116347
    test_connecting_nodes(5103, true).await
}

#[test(tokio::test)]
async fn connecting_to_http_server() -> Result<()> {
    // A comment line to avoid https://github.com/rust-lang/rust/issues/116347
    test_connecting_nodes(5104, false).await
}
