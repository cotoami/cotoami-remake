use std::{borrow::Cow, sync::Arc, time::Instant};

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use cotoami_node::prelude::*;
use futures::{stream::StreamExt, Stream};
use googletest::prelude::*;
use test_log::test;

pub mod common;

/// Test a [NodeService] by sending [Command]s.
/// Various service backends are defined in each test function below.
async fn test_service<S, C>(
    service: &S,
    backend_state: NodeState,
    changes: C,
    operator_node_id: Id<Node>,
) -> Result<()>
where
    S: NodeService + ?Sized,
    C: Stream<Item = ChangelogEntry>,
{
    let mut backend_ds = backend_state.db().new_session()?;
    let backend_owner = backend_state.local_node_as_operator()?;
    let (backend_local, backend_node) = backend_ds.local_node_pair(&backend_owner)?;
    let (backend_root_cotonoma, backend_root_coto) = backend_ds.try_get_local_node_root()?;
    futures::pin_mut!(changes);

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
            ..backend_node.clone()
        }),
        "Unexpected response of LocalNode command"
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: EnableAnonymousRead
    /////////////////////////////////////////////////////////////////////////////

    assert_that!(backend_local.anonymous_read_enabled, eq(false));

    let request = Command::EnableAnonymousRead { enable: true }.into_request();
    let enabled = service.call(request).await?.content::<bool>()?;
    assert_that!(enabled, eq(true));

    let (backend_local, backend_node) = backend_ds.local_node_pair(&backend_owner)?;
    assert_that!(backend_local.anonymous_read_enabled, eq(true));

    /////////////////////////////////////////////////////////////////////////////
    // Command: AddClient
    /////////////////////////////////////////////////////////////////////////////

    let new_client_id = Id::generate();
    let request = Command::AddClient(AddClient::new(new_client_id, NodeRole::Child, None::<&str>))
        .into_request();
    let client_added = service.call(request).await?.content::<ClientAdded>()?;

    assert_that!(
        client_added,
        pat!(ClientAdded {
            password: anything(), // auto-generated
            client: pat!(ClientNode {
                node_id: eq(&new_client_id),
                password_hash: eq(""), // should not be exposed
                session_token: none(), // should not be exposed
                disabled: eq(&false)
            }),
            // some fields will be set after the first login
            node: pat!(Node {
                name: eq(""),
                icon: eq(&Bytes::default()),
                version: eq(&0)
            })
        }),
        "Unexpected response of AddClient command"
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: EditClient
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::EditClient {
        id: new_client_id,
        values: EditClient {
            disabled: Some(true),
        },
    }
    .into_request();
    let updated_client = service.call(request).await?.content::<ClientNode>()?;

    assert_that!(
        updated_client,
        pat!(ClientNode {
            node_id: eq(&new_client_id),
            disabled: eq(&true)
        }),
        "Unexpected response of EditClient command"
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: ClientNode
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::ClientNode { id: new_client_id }.into_request();
    let client = service.call(request).await?.content::<ClientNode>()?;

    assert_that!(client, eq(&updated_client));

    /////////////////////////////////////////////////////////////////////////////
    // Command: PostCoto
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::PostCoto {
        input: CotoInput::new("Hello, Cotoami!"),
        post_to: backend_root_cotonoma.uuid,
    }
    .into_request();
    let posted_coto = service.call(request).await?.content::<Coto>()?;

    assert_that!(
        posted_coto,
        pat!(Coto {
            node_id: eq(&backend_node.uuid),
            posted_in_id: some(eq(&backend_root_cotonoma.uuid)),
            posted_by_id: eq(&operator_node_id),
            content: some(eq("Hello, Cotoami!")),
            summary: none(),
            is_cotonoma: eq(&false),
            repost_of_id: none(),
            reposted_in_ids: none(),
        }),
        "Unexpected response of PostCoto command"
    );
    assert_that!(
        changes.next().await,
        some(pat!(ChangelogEntry {
            origin_node_id: eq(&backend_node.uuid),
            change: pat!(Change::CreateCoto(eq(&Coto {
                rowid: 0,
                ..posted_coto
            })))
        })),
        "Unexpected changelogEntry on PostCoto command"
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: Connect
    /////////////////////////////////////////////////////////////////////////////

    let link_input =
        LinkInput::new(backend_root_coto.uuid, posted_coto.uuid).linking_phrase("The first link");
    let request = Command::Connect(link_input).into_request();
    let created_link = service.call(request).await?.content::<Link>()?;

    assert_that!(
        created_link,
        pat!(Link {
            node_id: eq(&backend_node.uuid),
            created_by_id: eq(&operator_node_id),
            source_coto_id: eq(&backend_root_coto.uuid),
            target_coto_id: eq(&posted_coto.uuid),
            linking_phrase: some(eq("The first link")),
            details: none(),
            order: eq(&1),
        }),
        "Unexpected response of Connect command"
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: OutgoingLinks
    /////////////////////////////////////////////////////////////////////////////

    let (coto2, _) = backend_ds.post_coto(
        &CotoInput::new("coto2"),
        &backend_root_cotonoma.uuid,
        &backend_owner,
    )?;
    let (link2, _) = backend_ds.connect(
        &LinkInput::new(backend_root_coto.uuid, coto2.uuid).linking_phrase("The second link"),
        &backend_owner,
    )?;

    let request = Command::OutgoingLinks {
        coto: backend_root_coto.uuid,
    }
    .into_request();
    let links = service.call(request).await?.content::<Vec<Link>>()?;

    assert_that!(
        links,
        unordered_elements_are![
            pat!(Link {
                linking_phrase: some(eq("The first link")),
                order: eq(&1),
            }),
            pat!(Link {
                linking_phrase: some(eq("The second link")),
                order: eq(&2),
            })
        ]
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: ChangeLinkOrder
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::ChangeLinkOrder {
        id: link2.uuid,
        new_order: 1,
    }
    .into_request();
    let updated_link = service.call(request).await?.content::<Link>()?;

    assert_that!(
        updated_link,
        pat!(Link {
            uuid: eq(&link2.uuid),
            order: eq(&1),
        }),
        "Unexpected response of ChangeLinkOrder command"
    );

    let links = backend_ds.outgoing_links(&[backend_root_coto.uuid])?;
    assert_that!(
        links,
        unordered_elements_are![
            pat!(Link {
                linking_phrase: some(eq("The second link")),
                order: eq(&1),
            }),
            pat!(Link {
                linking_phrase: some(eq("The first link")),
                order: eq(&2),
            })
        ]
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: EditLink
    /////////////////////////////////////////////////////////////////////////////

    let diff = LinkContentDiff::default()
        .linking_phrase(Some("Updated phrase"))
        .details(Some("Added details"));
    let request = Command::EditLink {
        id: created_link.uuid,
        diff,
    }
    .into_request();
    let updated_link = service.call(request).await?.content::<Link>()?;

    assert_that!(
        updated_link,
        pat!(Link {
            linking_phrase: some(eq("Updated phrase")),
            details: some(eq("Added details")),
        }),
        "Unexpected response of EditLink command"
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: Disconnect
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::Disconnect {
        id: created_link.uuid,
    }
    .into_request();
    let deleted_link_id = service.call(request).await?.content::<Id<Link>>()?;

    assert_that!(deleted_link_id, eq(created_link.uuid));

    let links = backend_ds.outgoing_links(&[backend_root_coto.uuid])?;
    assert_that!(
        links,
        unordered_elements_are![pat!(Link {
            linking_phrase: some(eq("The second link")),
            order: eq(&1),
        })]
    );

    Ok(())
}

#[test(tokio::test)]
async fn service_based_on_local_node() -> Result<()> {
    let config = common::new_node_config("test")?;
    let state = NodeState::new(config).await?;
    let operator_node_id = state.try_get_local_node_id()?;

    let service = OwnerOperatingService(state.clone());
    let changes = state.pubsub().changes().subscribe(None::<()>);

    test_service(&service, state, changes, operator_node_id).await
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
    let mut add_client = AddClient::new(client_id, client_role, Some("server-password"));
    add_client.as_owner = Some(true);
    let (server_state, shutdown) =
        common::launch_server_node("server", server_port, enable_websocket, add_client).await?;
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

    // Update the server to be an owner
    if let NodeRole::Parent = client_role {
        client_state
            .edit_child(
                server_id,
                EditChild {
                    as_owner: true,
                    can_edit_links: true,
                },
                Arc::new(client_state.local_node_as_operator()?),
            )
            .await
            .map_err(BackendServiceError)?;
        client_state
            .server_conns()
            .try_get(&server_id)?
            .reboot()
            .await;
    }

    // Parent service
    let parent_service = match client_role {
        NodeRole::Child => try_get_parent_service(&client_state, &server_id).await?,
        NodeRole::Parent => try_get_parent_service(&server_state, &client_id).await?,
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
    let _ = test_service(
        parent_service.as_ref(),
        match client_role {
            NodeRole::Child => server_state,
            NodeRole::Parent => client_state,
        },
        remote_changes,
        child_node_id,
    )
    .await?;

    shutdown.send(()).ok();

    Ok(())
}

async fn try_get_parent_service(
    child_state: &NodeState,
    parent_id: &Id<Node>,
) -> Result<Box<dyn NodeService>> {
    let start = Instant::now();
    let mut service = child_state.parent_services().get(parent_id);
    while service.is_none() && start.elapsed().as_secs() < 5 {
        tokio::task::yield_now().await;
        service = child_state.parent_services().get(parent_id);
    }
    service.ok_or(anyhow!("Couldn't get the parent service: {parent_id}"))
}

#[derive(Clone)]
struct OwnerOperatingService(NodeState);

impl Service<Request> for OwnerOperatingService {
    type Response = Response;
    type Error = anyhow::Error;
    type Future = NodeServiceFuture;

    fn call(&self, mut request: Request) -> Self::Future {
        let owner = self.0.local_node_as_operator().unwrap();
        request.set_from(Arc::new(owner));
        self.0.call(request)
    }
}

impl NodeService for OwnerOperatingService {
    fn description(&self) -> Cow<str> { self.0.description() }
}
