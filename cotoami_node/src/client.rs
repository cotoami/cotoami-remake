//! Network client implementations.

use std::sync::Arc;

use anyhow::Result;
use cotoami_db::{ChildNode, Id, Node, Operator};
use parking_lot::RwLock;

use crate::{
    event::remote::CommunicationError, service::models::NotConnected, state::NodeState, Abortables,
};

mod http;
mod retry;
mod sse;
mod ws;

pub use self::{http::HttpClient, sse::SseClient, ws::WebSocketClient};

#[derive(derive_more::Debug)]
pub(crate) struct ClientState {
    server_id: Id<Node>,
    server_as_operator: Option<Arc<Operator>>,
    child_privileges: Option<ChildNode>,
    conn_state: RwLock<ConnectionState>,
    #[debug(skip)]
    node_state: NodeState,
    #[debug(skip)]
    abortables: Abortables,
}

impl ClientState {
    pub(crate) async fn new(
        server_id: Id<Node>,
        child_privileges: Option<ChildNode>,
        node_state: NodeState,
    ) -> Result<Self> {
        Ok(Self {
            server_id,
            server_as_operator: node_state.as_operator(server_id).await?.map(Arc::new),
            child_privileges,
            conn_state: RwLock::new(ConnectionState::Disconnected(None)),
            node_state,
            abortables: Abortables::default(),
        })
    }

    pub fn child_privileges(&self) -> Option<&ChildNode> { self.child_privileges.as_ref() }

    fn is_server_parent(&self) -> bool { self.node_state.is_parent(&self.server_id) }

    fn change_conn_state(&self, state: ConnectionState) -> bool {
        let before = self.not_connected();
        *self.conn_state.write() = state;
        self.node_state.server_state_changed(
            self.server_id,
            before,
            self.not_connected(),
            self.child_privileges.clone(),
        )
    }

    fn not_connected(&self) -> Option<NotConnected> { self.conn_state.read().not_connected() }

    fn has_running_tasks(&self) -> bool { self.abortables.has_running_tasks() }

    fn disconnect(&self) -> bool {
        self.abortables.abort_all();
        self.change_conn_state(ConnectionState::Disconnected(None))
    }
}

#[derive(Debug)]
enum ConnectionState {
    // Newly connecting or reconnecting due to an error.
    Connecting(Option<anyhow::Error>),

    Connected,

    // Disconnected, which may be a result of an error.
    Disconnected(Option<CommunicationError>),
}

impl ConnectionState {
    pub fn connection_error(e: anyhow::Error) -> Self {
        ConnectionState::Disconnected(Some(CommunicationError::Connection(e)))
    }

    pub fn event_handling_failed(e: anyhow::Error) -> Self {
        ConnectionState::Disconnected(Some(CommunicationError::EventHandling(e)))
    }

    pub fn not_connected(&self) -> Option<NotConnected> {
        match self {
            ConnectionState::Connected => None,
            ConnectionState::Connecting(err) => Some(NotConnected::Connecting(
                err.as_ref().map(ToString::to_string),
            )),
            ConnectionState::Disconnected(err) => Some(NotConnected::Disconnected(
                err.as_ref().map(ToString::to_string),
            )),
        }
    }
}
