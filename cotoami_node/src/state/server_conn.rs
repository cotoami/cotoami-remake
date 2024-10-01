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

#[derive(Clone)]
pub struct ServerConnection {
    server: ServerNode,
    node_state: NodeState,
    local_as_child: Arc<RwLock<Option<ChildNode>>>,
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
            local_as_child: Default::default(),
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

        *self.local_as_child.write() = local_as_child;

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
        self.conn_state.write().disconnect();
        *self.local_as_child.write() = None;
        self.set_conn_state(ConnectionState::Disconnected(reason.map(String::from)));
    }

    pub fn not_connected(&self) -> Option<NotConnected> { self.conn_state.read().not_connected() }

    pub fn local_as_child(&self) -> Option<ChildNode> { self.local_as_child.read().clone() }

    fn to_parent(&self) -> bool { self.node_state.is_parent(&self.server.node_id) }

    fn set_conn_state(&self, state: ConnectionState) {
        let old_not_connected = self.not_connected();
        *self.conn_state.write() = state;
        let new_not_connected = self.not_connected();

        // Publish the state only if changed.
        if old_not_connected != new_not_connected {
            if let Some(not_connected) = new_not_connected {
                self.node_state.pubsub().events().server_disconnected(
                    self.server.node_id,
                    not_connected,
                    self.to_parent(),
                );
            } else {
                self.node_state
                    .pubsub()
                    .events()
                    .server_connected(self.server.node_id, self.local_as_child());
            }
        }
    }
}

impl ConnectionState {
    fn not_connected(&self) -> Option<NotConnected> {
        match self {
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

    fn disconnect(&mut self) {
        match self {
            ConnectionState::Initializing(task) => task.abort(),
            ConnectionState::WebSocket(client) => client.disconnect(),
            ConnectionState::Sse(client) => client.disconnect(),
            _ => (),
        }
    }
}

impl Drop for ConnectionState {
    fn drop(&mut self) { self.disconnect(); }
}
