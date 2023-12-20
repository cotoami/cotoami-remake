use std::{collections::HashMap, sync::Arc};

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use parking_lot::RwLock;
use tracing::{debug, info};

use crate::{
    client::{HttpClient, SseClient, WebSocketClient},
    service::{
        models::{CreateClientNodeSession, NotConnected},
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
    WebSocket(Arc<RwLock<WebSocketClient>>),
    Sse(Arc<RwLock<SseClient>>),
}

impl ServerConnection {
    pub async fn new(
        server: &ServerNode,
        http_client: HttpClient,
        node_state: &NodeState,
    ) -> Result<Self> {
        // Try to connect via WebSocket first
        let mut ws_client = WebSocketClient::new(
            server.node_id,
            server.url_prefix.clone(),
            node_state.clone(),
        )
        .await?;
        if let Ok(_) = ws_client.connect().await {
            Ok(Self::new_ws(ws_client))
        } else {
            // Fallback to SSE
            let mut sse_client =
                SseClient::new(server.node_id, http_client.clone(), node_state.clone()).await?;
            sse_client.connect();
            Ok(Self::new_sse(sse_client))
        }
    }

    fn new_ws(ws_client: WebSocketClient) -> Self {
        ServerConnection::WebSocket(Arc::new(RwLock::new(ws_client)))
    }

    fn new_sse(sse_client: SseClient) -> Self {
        ServerConnection::Sse(Arc::new(RwLock::new(sse_client)))
    }

    pub async fn connect(
        server_node: &ServerNode,
        local_node: Node,
        node_state: &NodeState,
    ) -> Self {
        if server_node.disabled {
            return Self::Disabled;
        }
        match Self::try_connect(server_node, local_node, node_state).await {
            Ok(conn) => conn,
            Err(e) => {
                debug!("Failed to initialize a server connection: {:?}", e);
                Self::InitFailed(Arc::new(e))
            }
        }
    }

    async fn try_connect(
        server: &ServerNode,
        local_node: Node,
        node_state: &NodeState,
    ) -> Result<Self> {
        let is_server_parent = node_state.is_parent(&server.node_id);
        let is_local_parent = !is_server_parent; // just for clarity
        let mut http_client = HttpClient::new(server.url_prefix.clone())?;

        // Attempt to log into the server node
        let password = server
            .password(node_state.config().owner_password())?
            .ok_or(anyhow!("Server password is missing."))?;
        let _ = http_client
            .create_client_node_session(CreateClientNodeSession {
                password,
                new_password: None,
                client: local_node,
                as_parent: Some(is_local_parent),
            })
            .await?;
        info!("Successfully logged in to {}", http_client.url_prefix());

        Self::new(server, http_client, node_state).await
    }

    pub fn disconnect(&mut self) {
        match self {
            ServerConnection::WebSocket(client) => client.write().disconnect(),
            ServerConnection::Sse(client) => client.write().disconnect(),
            _ => (),
        }
    }

    pub async fn reconnect(&mut self) -> Result<()> {
        match self {
            ServerConnection::WebSocket(client) => {
                client.write().connect().await?;
            }
            ServerConnection::Sse(client) => client.write().connect(),
            _ => (),
        }
        Ok(())
    }

    pub fn not_connected(&self) -> Option<NotConnected> {
        match self {
            ServerConnection::Disabled => Some(NotConnected::Disabled),
            ServerConnection::InitFailed(e) => Some(NotConnected::InitFailed(e.to_string())),
            ServerConnection::WebSocket(client) => client.read().not_connected(),
            ServerConnection::Sse(client) => client.read().not_connected(),
        }
    }
}

pub type ServerConnections = HashMap<Id<Node>, ServerConnection>;
