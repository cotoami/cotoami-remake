//! Network client implementations.

use std::{ops::DerefMut, sync::Arc};

use anyhow::Result;
use cotoami_db::{Id, Node, Operator};
use parking_lot::{Mutex, RwLock};
use tokio::task::{spawn_blocking, AbortHandle};
use tracing::info;

use crate::{event::EventLoopError, service::models::NotConnected, state::NodeState};

mod http;
mod sse;
mod ws;

pub use self::{http::HttpClient, sse::SseClient, ws::WebSocketClient};

struct ClientState {
    server_id: Id<Node>,
    server_as_operator: Option<Arc<Operator>>,
    conn_state: RwLock<ConnectionState>,
    node_state: NodeState,
    abortables: Arc<Mutex<Vec<AbortHandle>>>,
}

impl ClientState {
    async fn new(server_id: Id<Node>, node_state: NodeState) -> Result<Self> {
        let server_as_operator = spawn_blocking({
            let db = node_state.db().clone();
            move || db.new_session()?.as_operator(server_id)
        })
        .await??
        .map(Arc::new);

        Ok(Self {
            server_id,
            server_as_operator,
            conn_state: RwLock::new(ConnectionState::Disconnected(None)),
            node_state,
            abortables: Arc::new(Mutex::new(Vec::new())),
        })
    }

    fn is_server_parent(&self) -> bool { self.node_state.is_parent(&self.server_id) }

    fn set_conn_state(&self, state: ConnectionState) {
        let _ = std::mem::replace(self.conn_state.write().deref_mut(), state);
    }

    fn not_connected(&self) -> Option<NotConnected> { self.conn_state.read().not_connected() }

    fn has_running_tasks(&self) -> bool { !self.abortables.lock().is_empty() }

    fn add_abortable(&self, abortable: AbortHandle) { self.abortables.lock().push(abortable); }

    fn disconnect(&self) {
        info!("Disconnecting from: {}", self.server_id);
        let mut abortables = self.abortables.lock();
        while let Some(abortable) = abortables.pop() {
            abortable.abort();
        }
        self.set_conn_state(ConnectionState::Disconnected(None));
        self.publish_server_disconnected();
    }

    fn publish_server_disconnected(&self) {
        if let Some(not_connected) = self.not_connected() {
            self.node_state
                .pubsub()
                .events()
                .publish_server_disconnected(
                    self.server_id,
                    not_connected,
                    self.is_server_parent(),
                );
        }
    }
}

enum ConnectionState {
    // Newly connecting or reconnecting due to an error.
    Connecting(Option<anyhow::Error>),

    Connected,

    // Disconnected, which may be a result of an error.
    Disconnected(Option<EventLoopError>),
}

impl ConnectionState {
    pub fn communication_failed(e: anyhow::Error) -> Self {
        ConnectionState::Disconnected(Some(EventLoopError::CommunicationFailed(e)))
    }

    pub fn event_handling_failed(e: anyhow::Error) -> Self {
        ConnectionState::Disconnected(Some(EventLoopError::EventHandlingFailed(e)))
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
