use std::sync::Arc;

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use tracing::{debug, info};

use crate::{
    client::{HttpClient, SseClient, WebSocketClient},
    service::{
        models::{CreateClientNodeSession, NotConnected},
        RemoteNodeServiceExt,
    },
    state::NodeState,
};

#[derive(Clone)]
pub enum ServerConnection {
    Disabled,
    InitFailed(Arc<anyhow::Error>),
    WebSocket(WebSocketClient),
    Sse(SseClient),
}

impl ServerConnection {
    /// Connects to the given `server` to create a new [ServerConnection].
    pub async fn connect(server: &ServerNode, local_node: Node, node_state: &NodeState) -> Self {
        if server.disabled {
            return Self::Disabled;
        }
        match Self::try_connect(server, local_node, node_state).await {
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
        let mut http_client = HttpClient::new(&server.url_prefix)?;

        // Attempt to log into the server node
        let password = server
            .password(node_state.config().try_get_owner_password()?)?
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

    /// Creates a new connection to the given [ServerNode] using an
    /// [HttpClient] that has already logged it in (having a session token).
    pub async fn new(
        server: &ServerNode,
        http_client: HttpClient,
        node_state: &NodeState,
    ) -> Result<Self> {
        // Try to connect via WebSocket first
        let mut ws_client =
            WebSocketClient::new(server.node_id, &http_client, node_state.clone()).await?;
        match ws_client.connect().await {
            Ok(_) => Ok(Self::WebSocket(ws_client)),
            Err(e) => {
                // Fallback to SSE
                info!("Falling back to SSE due to: {e:?}");
                let mut sse_client =
                    SseClient::new(server.node_id, http_client.clone(), node_state.clone()).await?;
                sse_client.connect();
                Ok(Self::Sse(sse_client))
            }
        }
    }

    pub fn disconnect(&mut self) {
        match self {
            ServerConnection::WebSocket(client) => client.disconnect(),
            ServerConnection::Sse(client) => client.disconnect(),
            _ => (),
        }
    }

    pub async fn reconnect(&mut self) -> Result<()> {
        match self {
            ServerConnection::WebSocket(client) => {
                client.connect().await?;
            }
            ServerConnection::Sse(client) => client.connect(),
            _ => (),
        }
        Ok(())
    }

    pub fn not_connected(&self) -> Option<NotConnected> {
        match self {
            ServerConnection::Disabled => Some(NotConnected::Disabled),
            ServerConnection::InitFailed(e) => Some(NotConnected::InitFailed(e.to_string())),
            ServerConnection::WebSocket(client) => client.not_connected(),
            ServerConnection::Sse(client) => client.not_connected(),
        }
    }
}

impl Drop for ServerConnection {
    fn drop(&mut self) { self.disconnect(); }
}
