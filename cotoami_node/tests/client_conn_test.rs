use anyhow::Result;
use cotoami_node::prelude::*;
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

    // Connect the client to the server
    let server = common::connect_to_server(
        &client_state,
        format!("http://localhost:{server_port}"),
        "server-password",
        NodeRole::Child,
    )
    .await?;
    assert_that!(server.server.node_id, eq(server_id));

    // Client connections
    let client_conns = client_state.client_conns().active_clients();
    assert_eq!(client_conns.len(), 1);

    shutdown.send(()).ok();

    Ok(())
}

#[test(tokio::test)]
async fn connection_management_on_websocket_server() -> Result<()> {
    test_client_connection_management(5103, true).await
}

#[test(tokio::test)]
async fn connection_management_on_http_server() -> Result<()> {
    test_client_connection_management(5104, false).await
}
