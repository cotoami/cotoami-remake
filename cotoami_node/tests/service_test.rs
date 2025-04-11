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
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: LocalServer
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::LocalServer.into_request();
    let local_server = service.call(request).await?.content::<LocalServer>()?;

    let active_config = backend_state
        .local_server_config()
        .map(|arc| arc.as_ref().clone());
    assert_that!(
        local_server,
        pat!(LocalServer {
            active_config: eq(&active_config),
            anonymous_read_enabled: eq(&false),
            anonymous_connections: eq(&0)
        })
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
    );

    let child_node = backend_ds.try_get_child_node(&new_client_id, &backend_owner)?;
    assert_that!(
        child_node,
        pat!(ChildNode {
            node_id: eq(&new_client_id),
            as_owner: eq(&false),
            can_edit_itos: eq(&false)
        })
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
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: ClientNode
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::ClientNode { id: new_client_id }.into_request();
    let client = service.call(request).await?.content::<ClientNode>()?;

    assert_that!(client, eq(&updated_client));

    /////////////////////////////////////////////////////////////////////////////
    // Command: EditChild
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::EditChild {
        id: new_client_id,
        values: EditChild {
            as_owner: false,
            can_edit_itos: true,
        },
    }
    .into_request();
    let updated_child = service.call(request).await?.content::<ChildNode>()?;

    assert_that!(
        updated_child,
        pat!(ChildNode {
            node_id: eq(&new_client_id),
            as_owner: eq(&false),
            can_edit_itos: eq(&true)
        }),
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: ChildNode
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::ChildNode { id: new_client_id }.into_request();
    let child = service.call(request).await?.content::<ChildNode>()?;

    assert_that!(child, eq(&updated_child));

    /////////////////////////////////////////////////////////////////////////////
    // Command: GenerateClientPassword
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::GenerateClientPassword { id: new_client_id }.into_request();
    let new_password = service.call(request).await?.content::<String>()?;

    let client = backend_ds.try_get_client_node(&new_client_id, &backend_owner)?;
    client.as_principal().verify_password(&new_password)?;

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
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: Connect
    /////////////////////////////////////////////////////////////////////////////

    let ito_input =
        ItoInput::new(backend_root_coto.uuid, posted_coto.uuid).description("The first ito");
    let request = Command::Connect(ito_input).into_request();
    let created_ito = service.call(request).await?.content::<Ito>()?;

    assert_that!(
        created_ito,
        pat!(Ito {
            node_id: eq(&backend_node.uuid),
            created_by_id: eq(&operator_node_id),
            source_coto_id: eq(&backend_root_coto.uuid),
            target_coto_id: eq(&posted_coto.uuid),
            description: some(eq("The first ito")),
            details: none(),
            order: eq(&1),
        }),
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: OutgoingItos
    /////////////////////////////////////////////////////////////////////////////

    let (coto2, _) = backend_ds.post_coto(
        &CotoInput::new("coto2"),
        &backend_root_cotonoma.uuid,
        &backend_owner,
    )?;
    let (ito2, _) = backend_ds.connect(
        &ItoInput::new(backend_root_coto.uuid, coto2.uuid).description("The second ito"),
        &backend_owner,
    )?;

    let request = Command::OutgoingItos {
        coto: backend_root_coto.uuid,
    }
    .into_request();
    let itos = service.call(request).await?.content::<Vec<Ito>>()?;

    assert_that!(
        itos,
        unordered_elements_are![
            pat!(Ito {
                description: some(eq("The first ito")),
                order: eq(&1),
            }),
            pat!(Ito {
                description: some(eq("The second ito")),
                order: eq(&2),
            })
        ]
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: ChangeItoOrder
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::ChangeItoOrder {
        id: ito2.uuid,
        new_order: 1,
    }
    .into_request();
    let updated_ito = service.call(request).await?.content::<Ito>()?;

    assert_that!(
        updated_ito,
        pat!(Ito {
            uuid: eq(&ito2.uuid),
            order: eq(&1),
        }),
    );

    let itos = backend_ds.outgoing_itos(&[backend_root_coto.uuid])?;
    assert_that!(
        itos,
        unordered_elements_are![
            pat!(Ito {
                description: some(eq("The second ito")),
                order: eq(&1),
            }),
            pat!(Ito {
                description: some(eq("The first ito")),
                order: eq(&2),
            })
        ]
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: EditIto
    /////////////////////////////////////////////////////////////////////////////

    let diff = ItoContentDiff::default()
        .description(Some("Updated phrase"))
        .details(Some("Added details"));
    let request = Command::EditIto {
        id: created_ito.uuid,
        diff,
    }
    .into_request();
    let updated_ito = service.call(request).await?.content::<Ito>()?;

    assert_that!(
        updated_ito,
        pat!(Ito {
            description: some(eq("Updated phrase")),
            details: some(eq("Added details")),
        }),
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: Disconnect
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::Disconnect {
        id: created_ito.uuid,
    }
    .into_request();
    let deleted_ito_id = service.call(request).await?.content::<Id<Ito>>()?;

    assert_that!(deleted_ito_id, eq(created_ito.uuid));

    let itos = backend_ds.outgoing_itos(&[backend_root_coto.uuid])?;
    assert_that!(
        itos,
        unordered_elements_are![pat!(Ito {
            description: some(eq("The second ito")),
            order: eq(&1),
        })]
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: Promote
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::Promote {
        id: posted_coto.uuid,
    }
    .into_request();
    let (cotonoma, coto) = service.call(request).await?.content::<(Cotonoma, Coto)>()?;

    assert_that!(
        cotonoma,
        pat!(Cotonoma {
            node_id: eq(&backend_node.uuid),
            name: eq("Hello, Cotoami!"),
            coto_id: eq(&coto.uuid),
            created_at: eq(&coto.updated_at),
            updated_at: eq(&coto.updated_at),
        }),
    );
    assert_that!(
        coto,
        pat!(Coto {
            content: none(),
            summary: some(eq("Hello, Cotoami!")),
            is_cotonoma: eq(&true),
        }),
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: Cotonoma
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::Cotonoma { id: cotonoma.uuid }.into_request();
    let (cotonoma1, coto1) = service.call(request).await?.content::<(Cotonoma, Coto)>()?;

    assert_that!(cotonoma1, eq(&cotonoma));
    assert_that!(coto1, eq(&coto));

    /////////////////////////////////////////////////////////////////////////////
    // Command: CotonomaByCotoId
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::CotonomaByCotoId { id: coto.uuid }.into_request();
    let (cotonoma2, coto2) = service.call(request).await?.content::<(Cotonoma, Coto)>()?;

    assert_that!(cotonoma2, eq(&cotonoma));
    assert_that!(coto2, eq(&coto));

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
                    can_edit_itos: true,
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
