use std::{
    ops::{Deref, DerefMut},
    sync::Arc,
};

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use parking_lot::RwLock;
use tokio::task::AbortHandle;
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
pub struct ServerConnection {
    server: ServerNode,
    node_state: NodeState,
    conn_state: Arc<RwLock<ConnectionState>>,
}

enum ConnectionState {
    Disconnected(Option<String>),
    Disabled,
    Initializing(AbortHandle),
    InitFailed(Arc<anyhow::Error>),
    WebSocket(WebSocketClient),
    Sse(SseClient),
}

impl ServerConnection {
    pub fn new(server: ServerNode, node_state: NodeState) -> Self {
        let conn_state = if server.disabled {
            ConnectionState::Disabled
        } else {
            ConnectionState::Disconnected(None)
        };
        Self {
            server,
            node_state,
            conn_state: Arc::new(RwLock::new(conn_state)),
        }
    }

    /// Connects to the given `server` to create a new [ServerConnection].
    pub async fn connect(&self) {
        if self.server.disabled {
            return;
        }
        self.disconnect(None);

        let task = tokio::spawn(self.clone().try_connect());
        self.set_conn_state(ConnectionState::Initializing(task.abort_handle()));
        if let Ok(Err(e)) = task.await {
            debug!("Failed to initialize a server connection: {:?}", e);
            self.set_conn_state(ConnectionState::InitFailed(Arc::new(e)));
        }
    }

    async fn try_connect(self) -> Result<()> {
        let is_server_parent = self.node_state.is_parent(&self.server.node_id);
        let is_local_parent = !is_server_parent; // just for clarity
        let (_, local_node) = self.node_state.local_node_pair().await?;
        let mut http_client = HttpClient::new(&self.server.url_prefix)?;

        // Attempt to log into the server node
        let password = self
            .server
            .password(self.node_state.config().try_get_owner_password()?)?
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

        self.start_event_loop(http_client).await
    }

    /// Starts a event loop using an [HttpClient] that has already logged it in
    /// (having a session token).
    pub async fn start_event_loop(&self, http_client: HttpClient) -> Result<()> {
        if self.server.disabled {
            return Ok(());
        }

        // Try to connect via WebSocket first
        let mut ws_client =
            WebSocketClient::new(self.server.node_id, &http_client, self.node_state.clone())
                .await?;
        match ws_client.connect().await {
            Ok(_) => self.set_conn_state(ConnectionState::WebSocket(ws_client)),
            Err(e) => {
                // Fallback to SSE
                info!("Falling back to SSE due to: {e:?}");
                let mut sse_client = SseClient::new(
                    self.server.node_id,
                    http_client.clone(),
                    self.node_state.clone(),
                )
                .await?;
                sse_client.connect();
                self.set_conn_state(ConnectionState::Sse(sse_client));
            }
        }
        Ok(())
    }

    pub fn disable(&self) {
        self.disconnect(None);
        self.set_conn_state(ConnectionState::Disabled);
    }

    pub fn disconnect(&self, reason: Option<&str>) {
        match self.conn_state.write().deref_mut() {
            ConnectionState::Initializing(task) => task.abort(),
            ConnectionState::WebSocket(client) => client.disconnect(),
            ConnectionState::Sse(client) => client.disconnect(),
            _ => (),
        }
        self.set_conn_state(ConnectionState::Disconnected(reason.map(String::from)));
    }

    pub fn not_connected(&self) -> Option<NotConnected> {
        match self.conn_state.read().deref() {
            ConnectionState::Disconnected(reason) => {
                Some(NotConnected::Disconnected(reason.clone()))
            }
            ConnectionState::Initializing(_) => Some(NotConnected::Connecting(None)),
            ConnectionState::Disabled => Some(NotConnected::Disabled),
            ConnectionState::InitFailed(e) => Some(NotConnected::InitFailed(e.to_string())),
            ConnectionState::WebSocket(client) => client.not_connected(),
            ConnectionState::Sse(client) => client.not_connected(),
        }
    }

    fn set_conn_state(&self, state: ConnectionState) {
        *self.conn_state.write() = state;
        // TODO publish state change
    }
}

impl Drop for ServerConnection {
    fn drop(&mut self) { self.disconnect(None); }
}
