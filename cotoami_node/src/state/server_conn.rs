use std::sync::Arc;

use anyhow::{anyhow, bail, Result};
use cotoami_db::prelude::*;
use parking_lot::RwLock;
use tokio::task::AbortHandle;
use tracing::{debug, info};

use crate::{
    client::{HttpClient, SseClient, WebSocketClient},
    service::{
        models::{CreateClientNodeSession, NodeRole, NotConnected},
        RemoteNodeServiceExt,
    },
    state::NodeState,
};

#[derive(derive_more::Debug, Clone)]
pub struct ServerConnection {
    server: ServerNode,
    #[debug(skip)]
    node_state: NodeState,
    conn_state: Arc<RwLock<ConnectionState>>,
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

    /// Connects to the server node.
    ///
    /// The state of this connection will be published via
    /// [crate::event::local::LocalNodeEvent].
    pub async fn connect(&self) {
        if self.server.disabled || self.not_connected().is_none() {
            return;
        }

        debug!("Server connection initializing: {}", self.server.node_id);
        let task = tokio::spawn(self.clone().try_connect());
        self.set_conn_state(ConnectionState::Initializing(task.abort_handle()));

        match task.await {
            Ok(Ok(())) => {
                debug!("Server connection established: {}", self.server.node_id);
            }
            Ok(Err(e)) => {
                debug!("Failed to initialize a server connection: {:?}", e);
                self.set_conn_state(ConnectionState::InitFailed(Arc::new(e)));
            }
            Err(e) => {
                debug!("Initializing task has been aborted: {:?}", e);
            }
        }
    }

    async fn try_connect(self) -> Result<()> {
        let (_, local_node) = self.node_state.local_node_pair().await?;
        let mut http_client = HttpClient::new(&self.server.url_prefix)?;

        // Attempt to log into the server node
        let master_password = self.node_state.config().try_get_owner_password()?;
        let password = self
            .server
            .password(master_password)?
            .ok_or(anyhow!("Server password is missing."))?;
        let session = http_client
            .create_client_node_session(CreateClientNodeSession {
                password,
                new_password: None,
                client: local_node,
                client_role: if self.to_parent() {
                    Some(NodeRole::Child)
                } else {
                    Some(NodeRole::Parent)
                },
            })
            .await?;
        info!("Successfully logged in to {}", http_client.url_prefix());

        if session.server.uuid != self.server.node_id {
            bail!("The remote server ID does not match the stored value.");
        }

        self.start_event_loop(http_client, session.as_child).await
    }

    /// Starts a event loop using an [HttpClient] that already has a session token.
    pub async fn start_event_loop(
        &self,
        http_client: HttpClient,
        local_as_child: Option<ChildNode>,
    ) -> Result<()> {
        if self.server.disabled {
            return Ok(());
        }

        // Try to connect via WebSocket first
        let mut ws_client = WebSocketClient::new(
            self.server.node_id,
            local_as_child.clone(),
            &http_client,
            self.node_state.clone(),
        )
        .await?;
        match ws_client.connect().await {
            Ok(_) => self.set_conn_state(ConnectionState::WebSocket(ws_client)),
            Err(e) => {
                // Fallback to SSE
                info!("Falling back to SSE due to: {e:?}");
                let mut sse_client = SseClient::new(
                    self.server.node_id,
                    local_as_child,
                    http_client.clone(),
                    self.node_state.clone(),
                )
                .await?;
                sse_client.connect().await?;
                self.set_conn_state(ConnectionState::Sse(sse_client));
            }
        }
        Ok(())
    }

    pub fn disable(&self) {
        let disconnected = self.conn_state.write().disconnect();
        self.set_conn_state(ConnectionState::Disabled);
        if disconnected {
            self.node_state
                .server_disconnected(self.server.node_id, self.not_connected().unwrap());
        }
    }

    pub fn disconnect(&self, reason: Option<&str>) {
        if self.conn_state.write().disconnect() {
            self.set_conn_state(ConnectionState::Disconnected(reason.map(String::from)));
            self.node_state
                .server_disconnected(self.server.node_id, self.not_connected().unwrap());
        }
    }

    pub async fn reboot(&self) {
        self.disconnect(None);
        self.connect().await;
    }

    pub fn client_as_child(&self) -> Option<ChildNode> {
        self.conn_state.read().client_as_child().cloned()
    }

    pub fn not_connected(&self) -> Option<NotConnected> { self.conn_state.read().not_connected() }

    fn to_parent(&self) -> bool { self.node_state.is_parent(&self.server.node_id) }

    fn set_conn_state(&self, state: ConnectionState) { *self.conn_state.write() = state; }
}

#[derive(Debug)]
enum ConnectionState {
    Disconnected(Option<String>),
    Disabled,
    Initializing(AbortHandle),
    InitFailed(Arc<anyhow::Error>),
    WebSocket(WebSocketClient),
    Sse(SseClient),
}
impl ConnectionState {
    fn client_as_child(&self) -> Option<&ChildNode> {
        match self {
            Self::WebSocket(client) => client.as_child(),
            Self::Sse(client) => client.as_child(),
            _ => None,
        }
    }

    fn not_connected(&self) -> Option<NotConnected> {
        match self {
            Self::Disconnected(reason) => Some(NotConnected::Disconnected(reason.clone())),
            Self::Initializing(_) => Some(NotConnected::Connecting(None)),
            Self::Disabled => Some(NotConnected::Disabled),
            Self::InitFailed(e) => Some(NotConnected::InitFailed(e.to_string())),
            Self::WebSocket(client) => client.not_connected(),
            Self::Sse(client) => client.not_connected(),
        }
    }

    fn disconnect(&mut self) -> bool {
        match self {
            Self::Initializing(task) => {
                task.abort();
                true
            }
            Self::WebSocket(client) => client.disconnect(),
            Self::Sse(client) => client.disconnect(),
            _ => false,
        }
    }
}

impl Drop for ConnectionState {
    fn drop(&mut self) { self.disconnect(); }
}
