use anyhow::Result;
use cotoami_db::ChildNodeInput;
use cotoami_node::prelude::*;
use futures::stream::StreamExt;
use googletest::prelude::*;
use test_log::test;

pub mod common;

use self::common::wait_get;

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
        AddClient::new(
            client_id,
            Some("server-password"),
            Some(ChildNodeInput::default()),
        ),
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
        wait_get(server_events.next(), "ClientConnected event").await,
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
        // The first two events are in no particular order.
        vec![
            wait_get(client_events.next(), "First event at client").await,
            wait_get(client_events.next(), "Second event at client").await,
        ],
        unordered_elements_are![
            some(pat!(LocalNodeEvent::ServerStateChanged {
                node_id: eq(&server_id),
                not_connected: none() // It means "connected".
            })),
            some(pat!(LocalNodeEvent::ParentRegistered {
                node_id: eq(&server_id),
            }))
        ]
    );
    assert_that!(
        client_state.server_conns().get(&server_id),
        some(pat!(ServerConnection {
            not_connected(): none()
        }))
    );

    assert_that!(
        wait_get(client_events.next(), "ParentSyncStart event").await,
        some(pat!(LocalNodeEvent::ParentSyncStart {
            node_id: eq(&server_id),
        }))
    );
    assert_that!(
        wait_get(client_events.next(), "First ParentSyncProgress event").await,
        some(pat!(LocalNodeEvent::ParentSyncProgress {
            node_id: eq(&server_id),
            progress: eq(&0),
            total: eq(&2)
        }))
    );
    assert_that!(
        wait_get(client_events.next(), "Second ParentSyncProgress event").await,
        some(pat!(LocalNodeEvent::ParentSyncProgress {
            node_id: eq(&server_id),
            progress: eq(&2),
            total: eq(&2)
        }))
    );
    assert_that!(
        wait_get(client_events.next(), "ParentSyncEnd event").await,
        some(pat!(LocalNodeEvent::ParentSyncEnd {
            node_id: eq(&server_id),
            range: some(eq(&(1, 2))),
            error: none()
        }))
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: manual disconnection from server-side
    /////////////////////////////////////////////////////////////////////////////

    server_state.client_conns().disconnect(&client_id);

    // Server-side
    assert_that!(
        wait_get(server_events.next(), "ClientDisconnected event").await,
        some(pat!(LocalNodeEvent::ClientDisconnected {
            node_id: eq(&client_id),
            error: none()
        }))
    );
    assert_that!(server_state.client_conns().active_clients().len(), eq(0));

    // Client-side
    assert_that!(
        wait_get(client_events.next(), "ServerStateChanged event").await,
        some(pat!(LocalNodeEvent::ServerStateChanged {
            node_id: eq(&server_id),
            not_connected: some(pat!(NotConnected::Connecting(some(anything()))))
        })),
        "Client should try to reconnect after manual disconnection."
    );
    assert_that!(
        wait_get(client_events.next(), "ParentDisconnected event").await,
        some(pat!(LocalNodeEvent::ParentDisconnected {
            node_id: eq(&server_id),
        }))
    );
    assert_that!(
        client_state.server_conns().get(&server_id),
        some(pat!(ServerConnection {
            not_connected(): some(anything())
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
