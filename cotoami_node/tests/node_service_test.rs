use anyhow::Result;
use cotoami_db::prelude::*;
use cotoami_node::prelude::*;
use futures::Stream;
use googletest::prelude::*;
use test_log::test;

pub mod common;

/// Test a [NodeService] by sending [Command]s.
/// Various service backends are defined in each test function below.
async fn test_node_service<S, C>(
    service: &S,
    backend_ds: &mut DatabaseSession<'_>,
    changes: C,
    child_node_id: Id<Node>,
) -> Result<()>
where
    S: NodeService + ?Sized,
    C: Stream<Item = ChangelogEntry>,
{
    let service_node = backend_ds.local_node()?;
    let root_cotonoma_id = service_node.root_cotonoma_id.unwrap();

    /////////////////////////////////////////////////////////////////////////////
    // Command: LocalNode
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::LocalNode.into_request();
    let request_id = *request.id();
    let response = service.call(request).await?;

    assert_that!(response.id(), eq(&request_id));
    assert_that!(
        response.content::<Node>()?,
        eq(&Node {
            rowid: 0, // skip_deserializing
            ..service_node.clone()
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: PostCoto
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::PostCoto {
        input: CotoInput::new("Hello, Cotoami!"),
        post_to: root_cotonoma_id,
    }
    .into_request();
    let posted_coto = service.call(request).await?.content::<Coto>()?;

    assert_that!(
        posted_coto,
        pat!(Coto {
            node_id: eq(&service_node.uuid),
            posted_in_id: some(eq(&root_cotonoma_id)),
            posted_by_id: eq(&child_node_id),
            content: some(eq("Hello, Cotoami!")),
            summary: none(),
            is_cotonoma: eq(&false),
            repost_of_id: none(),
            reposted_in_ids: none(),
        })
    );

    Ok(())
}

#[test(tokio::test)]
async fn service_based_on_local_node() -> Result<()> {
    let config = common::new_node_config("test")?;
    let state = NodeState::new(config).await?;
    let mut ds = state.db().new_session()?;
    let changes = state.pubsub().changes().subscribe(None::<()>);

    test_node_service(&state, &mut ds, changes, state.try_get_local_node_id()?).await
}

#[test(tokio::test)]
async fn service_based_on_websocket_server() -> Result<()> {
    test_service_based_on_remote_node(5103, true, NodeRole::Child).await
}

#[test(tokio::test)]
async fn service_based_on_websocket_client() -> Result<()> {
    test_service_based_on_remote_node(5104, true, NodeRole::Parent).await
}

#[test(tokio::test)]
async fn service_based_on_http_server() -> Result<()> {
    test_service_based_on_remote_node(5105, false, NodeRole::Child).await
}

#[test(tokio::test)]
async fn service_based_on_http_client() -> Result<()> {
    test_service_based_on_remote_node(5106, false, NodeRole::Parent).await
}

async fn test_service_based_on_remote_node(
    server_port: u16,
    enable_websocket: bool,
    client_role: NodeRole,
) -> Result<()> {
    // Client node
    let client_state = common::new_client_node_state("client").await?;
    let client_id = client_state.try_get_local_node_id()?;

    // Server node
    let (server_state, shutdown) = common::launch_server_node(
        "server",
        server_port,
        enable_websocket,
        AddClient::new(client_id, "server-password", client_role),
    )
    .await?;
    let server_id = server_state.try_get_local_node_id()?;

    // Connect the client to the server
    let server = common::connect_to_server(
        &client_state,
        format!("http://localhost:{server_port}"),
        "server-password",
        client_role,
    )
    .await?;
    assert_that!(server.server.node_id, eq(server_id));

    // Parent service
    let parent_service = match client_role {
        NodeRole::Child => common::get_parent_service(&client_state, &server_id).await?,
        NodeRole::Parent => common::get_parent_service(&server_state, &client_id).await?,
    };
    let expected_service_description = format!(
        "{} {}-as-parent: {}",
        if enable_websocket {
            "WebSocket"
        } else {
            match client_role {
                NodeRole::Child => "HTTP",
                NodeRole::Parent => "SSE",
            }
        },
        match client_role {
            NodeRole::Child => "server",
            NodeRole::Parent => "client",
        },
        match client_role {
            NodeRole::Child =>
                if enable_websocket {
                    format!("ws://localhost:{}/api/ws", server_port)
                } else {
                    format!("http://localhost:{}/", server_port)
                },
            NodeRole::Parent => client_id.to_string(),
        },
    );
    println!("expected_service_description: {expected_service_description}");
    assert_that!(
        parent_service.description(),
        eq(&expected_service_description)
    );

    // Parent DatabaseSession
    let mut parent_ds = match client_role {
        NodeRole::Child => server_state.db().new_session()?,
        NodeRole::Parent => client_state.db().new_session()?,
    };

    // Remote changes
    let remote_changes = match client_role {
        NodeRole::Child => client_state
            .pubsub()
            .remote_changes()
            .subscribe(Some(server_id)),
        NodeRole::Parent => server_state
            .pubsub()
            .remote_changes()
            .subscribe(Some(client_id)),
    };

    // Child node ID
    let child_node_id = match client_role {
        NodeRole::Child => client_id,
        NodeRole::Parent => server_id,
    };

    // Test the parent service
    let _ = test_node_service(
        parent_service.as_ref(),
        &mut parent_ds,
        remote_changes,
        child_node_id,
    )
    .await?;

    shutdown.send(()).ok();

    Ok(())
}
