use std::{collections::HashMap, sync::Arc};

use anyhow::{anyhow, ensure, Result};
use cotoami_db::prelude::*;
use parking_lot::RwLock;
use tokio::task::AbortHandle;
use tracing::{debug, info};

use crate::{
    client::{ClientState, HttpClient, SseClient, WebSocketClient},
    service::{
        models::{CreateClientNodeSession, NodeRole, NotConnected},
        RemoteNodeServiceExt,
    },
    state::NodeState,
};

/////////////////////////////////////////////////////////////////////////////
// ServerConnection
/////////////////////////////////////////////////////////////////////////////

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
        self.set_conn_state(ConnectionState::Initializing(task.abort_handle()), true);

        match task.await {
            Ok(Ok(())) => {
                debug!("Server connection established: {}", self.server.node_id);
            }
            Ok(Err(e)) => {
                debug!("Failed to initialize a server connection: {:?}", e);
                self.set_conn_state(ConnectionState::InitFailed(Arc::new(e)), true);
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
        let password = self
            .server
            .password(self.node_state.read_config().try_get_owner_password()?)?;
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

        ensure!(
            session.server.uuid == self.server.node_id,
            "The remote node ID does not match the registered server ID."
        );

        self.start_event_loop(http_client, session.child_privileges)
            .await
    }

    /// Starts a event loop using an [HttpClient] that already has a session token.
    pub async fn start_event_loop(
        &self,
        http_client: HttpClient,
        child_privileges: Option<ChildNode>,
    ) -> Result<()> {
        if self.server.disabled {
            return Ok(());
        }

        // Try to connect via WebSocket first
        let mut ws_client = WebSocketClient::new(
            self.new_client_state(child_privileges.clone()).await?,
            &http_client,
        )
        .await?;
        match ws_client.connect().await {
            Ok(_) => self.set_conn_state(ConnectionState::WebSocket(ws_client), false),
            Err(e) => {
                // Fallback to SSE
                info!("Falling back to SSE due to: {e:?}");
                let mut sse_client =
                    SseClient::new(self.new_client_state(child_privileges).await?, http_client)
                        .await?;
                sse_client.connect().await?;
                self.set_conn_state(ConnectionState::Sse(sse_client), false);
            }
        }
        Ok(())
    }

    async fn new_client_state(&self, as_child: Option<ChildNode>) -> Result<ClientState> {
        ClientState::new(self.server.node_id, as_child, self.node_state.clone()).await
    }

    pub fn disable(&self) {
        self.conn_state.write().disconnect();
        self.set_conn_state(ConnectionState::Disabled, true);
    }

    pub fn disconnect(&self, reason: Option<&str>) {
        self.conn_state.write().disconnect();
        self.set_conn_state(
            ConnectionState::Disconnected(reason.map(String::from)),
            true,
        );
    }

    pub async fn reboot(&self) {
        self.disconnect(None);
        self.connect().await;
    }

    pub fn child_privileges(&self) -> Option<ChildNode> {
        self.conn_state.read().child_privileges().cloned()
    }

    pub fn not_connected(&self) -> Option<NotConnected> { self.conn_state.read().not_connected() }

    fn to_parent(&self) -> bool { self.node_state.is_parent(&self.server.node_id) }

    fn set_conn_state(&self, state: ConnectionState, notify_change: bool) {
        let before = self.not_connected();
        *self.conn_state.write() = state;
        if notify_change {
            self.node_state.server_state_changed(
                self.server.node_id,
                before,
                self.not_connected(),
                self.child_privileges(),
            );
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// ConnectionState
/////////////////////////////////////////////////////////////////////////////

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
    fn child_privileges(&self) -> Option<&ChildNode> {
        match self {
            Self::WebSocket(client) => client.child_privileges(),
            Self::Sse(client) => client.child_privileges(),
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

/////////////////////////////////////////////////////////////////////////////
// ServerConnections
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, Default)]
pub struct ServerConnections(
    #[allow(clippy::type_complexity)] Arc<RwLock<HashMap<Id<Node>, ServerConnection>>>,
);

impl ServerConnections {
    pub fn contains(&self, server_id: &Id<Node>) -> bool { self.0.read().contains_key(server_id) }

    pub fn get(&self, server_id: &Id<Node>) -> Option<ServerConnection> {
        self.0.read().get(server_id).cloned()
    }

    pub fn try_get(&self, server_id: &Id<Node>) -> Result<ServerConnection> {
        self.get(server_id).ok_or(anyhow!(DatabaseError::not_found(
            EntityKind::ServerNode,
            *server_id,
        )))
    }

    pub(crate) fn put(&self, server_id: Id<Node>, server_conn: ServerConnection) {
        self.0.write().insert(server_id, server_conn);
    }

    #[allow(clippy::await_holding_lock)]
    pub async fn connect_all(&self) {
        // Configured the `send_guard` feature of parking_lot to be enabled,
        // so that the each read lock guard can be held across calls to .await.
        // https://github.com/Amanieu/parking_lot/issues/197
        for conn in self.0.read().values() {
            conn.connect().await;
        }
    }

    pub fn disconnect_all(&self) {
        for conn in self.0.read().values() {
            conn.disconnect(None);
        }
    }
}
