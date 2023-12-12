//! Network client implementations.

use tokio::task::JoinSet;

mod http;
mod sse;
mod ws;

pub use self::{
    http::HttpClient,
    sse::{SseClient, SseClientError, SseClientState},
};
use crate::service::models::NotConnected;

struct ClientState {
    tasks: JoinSet<()>,
    connection: ConnectionState,
}

impl ClientState {
    pub fn disconnect(&mut self) {
        self.tasks.abort_all();
        self.connection = ConnectionState::Disconnected(None);
    }

    pub fn connection(&self) -> &ConnectionState { &self.connection }

    pub fn not_connected(&self) -> Option<NotConnected> {
        match self.connection() {
            ConnectionState::Connected => None,
            ConnectionState::Connecting(err) => Some(NotConnected::Connecting(
                err.as_ref().map(ToString::to_string),
            )),
            ConnectionState::Disconnected(Some(err)) => match err {
                ClientError::InitFailed(e) => Some(NotConnected::InitFailed(e.to_string())),
                ClientError::StreamFailed(e) => Some(NotConnected::StreamFailed(e.to_string())),
                ClientError::EventHandlingFailed(e) => {
                    Some(NotConnected::EventHandlingFailed(e.to_string()))
                }
            },
            ConnectionState::Disconnected(None) => Some(NotConnected::Disabled),
        }
    }
}

enum ConnectionState {
    // Newly connecting or reconnecting due to an error
    Connecting(Option<anyhow::Error>),

    Connected,

    // Disconnected, which may be a result of an error
    Disconnected(Option<ClientError>),
}

enum ClientError {
    InitFailed(anyhow::Error),
    StreamFailed(anyhow::Error),
    EventHandlingFailed(anyhow::Error),
}
