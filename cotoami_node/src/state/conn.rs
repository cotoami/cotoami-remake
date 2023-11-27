use std::{collections::HashMap, sync::Arc};

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use parking_lot::RwLock;
use tracing::{debug, info};

use crate::{
    client::{HttpClient, SseClient, SseClientState},
    service::{
        models::{CreateClientNodeSession, NotConnected, Session},
        RemoteNodeServiceExt,
    },
    state::NodeState,
};

/////////////////////////////////////////////////////////////////////////////
// ServerConnection
/////////////////////////////////////////////////////////////////////////////

#[derive(Clone)]
pub enum ServerConnection {
    Disabled,
    InitFailed(Arc<anyhow::Error>),
    SseConnected(Arc<SseConnection>),
}

impl ServerConnection {
    pub fn new_sse(session: Session, http_client: HttpClient, mut sse_client: SseClient) -> Self {
        let server_conn = ServerConnection::SseConnected(Arc::new(SseConnection {
            session,
            http_client,
            sse_client_state: sse_client.state(),
        }));
        tokio::spawn(async move {
            sse_client.start().await;
        });
        server_conn
    }

    pub async fn connect_sse(
        server_node: &ServerNode,
        local_node: Node,
        node_state: &NodeState,
    ) -> Self {
        match Self::try_connect_sse(server_node, local_node, node_state).await {
            Ok(conn) => conn,
            Err(e) => {
                debug!("Failed to initialize a server connection: {:?}", e);
                ServerConnection::InitFailed(Arc::new(e))
            }
        }
    }

    async fn try_connect_sse(
        server_node: &ServerNode,
        local_node: Node,
        node_state: &NodeState,
    ) -> Result<Self> {
        let is_server_parent = node_state.is_parent(&server_node.node_id);
        let mut http_client = HttpClient::new(server_node.url_prefix.clone())?;

        // Attempt to log into the server node
        let password = server_node
            .password(node_state.config().owner_password())?
            .ok_or(anyhow!("Server password is missing."))?;
        let client_session = http_client
            .create_client_node_session(CreateClientNodeSession {
                password,
                new_password: None,
                client: local_node,
                as_parent: Some(is_server_parent),
            })
            .await?;
        info!("Successfully logged in to {}", http_client.url_prefix());

        // Sync with the parent
        if is_server_parent {
            node_state
                .sync_with_parent(server_node.node_id, Box::new(http_client.clone()))
                .await?;
        }

        // Create a SSE client
        let sse_client =
            SseClient::new(server_node.node_id, http_client.clone(), node_state.clone())?;

        Ok(Self::new_sse(
            client_session.session,
            http_client,
            sse_client,
        ))
    }

    pub fn disable(&self) {
        match self {
            ServerConnection::SseConnected(conn) => conn.disable(),
            _ => (),
        }
    }

    pub fn enable_if_possible(&self) -> bool {
        match self {
            ServerConnection::SseConnected(conn) => conn.enable_if_possible(),
            _ => false,
        }
    }

    pub fn not_connected(&self) -> Option<NotConnected> {
        match self {
            ServerConnection::Disabled => Some(NotConnected::Disabled),
            ServerConnection::InitFailed(e) => Some(NotConnected::InitFailed(e.to_string())),
            ServerConnection::SseConnected(conn) => conn.not_connected(),
        }
    }
}

pub type ServerConnections = HashMap<Id<Node>, ServerConnection>;

/////////////////////////////////////////////////////////////////////////////
// SseConnection
/////////////////////////////////////////////////////////////////////////////

pub struct SseConnection {
    session: Session,
    http_client: HttpClient,
    sse_client_state: Arc<RwLock<SseClientState>>,
}

impl SseConnection {
    pub fn disable(&self) { self.sse_client_state.write().disable(); }

    pub fn enable_if_possible(&self) -> bool { self.sse_client_state.write().enable_if_possible() }

    pub fn not_connected(&self) -> Option<NotConnected> {
        self.sse_client_state.read().not_connected()
    }
}
