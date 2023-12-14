use std::{collections::HashMap, sync::Arc};

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use parking_lot::RwLock;
use tracing::{debug, info};

use crate::{
    client::{HttpClient, SseClient},
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
    Sse(Arc<RwLock<SseClient>>),
}

impl ServerConnection {
    pub fn new_sse(sse_client: SseClient) -> Self {
        ServerConnection::Sse(Arc::new(RwLock::new(sse_client)))
    }

    pub async fn connect_sse(
        server_node: &ServerNode,
        local_node: Node,
        node_state: &NodeState,
    ) -> Self {
        if server_node.disabled {
            return Self::Disabled;
        }
        match Self::try_connect_sse(server_node, local_node, node_state).await {
            Ok(conn) => conn,
            Err(e) => {
                debug!("Failed to initialize a server connection: {:?}", e);
                Self::InitFailed(Arc::new(e))
            }
        }
    }

    async fn try_connect_sse(
        server_node: &ServerNode,
        local_node: Node,
        node_state: &NodeState,
    ) -> Result<Self> {
        let is_server_parent = node_state.is_parent(&server_node.node_id);
        let is_local_parent = !is_server_parent; // just for clarity
        let mut http_client = HttpClient::new(server_node.url_prefix.clone())?;

        // Attempt to log into the server node
        let password = server_node
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

        // Sync with the parent
        if is_server_parent {
            node_state
                .sync_with_parent(server_node.node_id, Box::new(http_client.clone()))
                .await?;
        }

        // Create a SSE client
        let mut sse_client =
            SseClient::new(server_node.node_id, http_client.clone(), node_state.clone()).await?;
        sse_client.connect();

        Ok(Self::new_sse(sse_client))
    }

    pub fn disconnect(&mut self) {
        match self {
            ServerConnection::Sse(client) => client.write().disconnect(),
            _ => (),
        }
    }

    pub fn reconnect(&mut self) {
        match self {
            ServerConnection::Sse(client) => client.write().connect(),
            _ => (),
        }
    }

    pub fn not_connected(&self) -> Option<NotConnected> {
        match self {
            ServerConnection::Disabled => Some(NotConnected::Disabled),
            ServerConnection::InitFailed(e) => Some(NotConnected::InitFailed(e.to_string())),
            ServerConnection::Sse(client) => client.read().not_connected(),
        }
    }
}

pub type ServerConnections = HashMap<Id<Node>, ServerConnection>;
