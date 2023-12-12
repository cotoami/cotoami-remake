//! Network client implementations.

mod http;
mod sse;
mod ws;

pub use self::{http::HttpClient, sse::SseClient};
use crate::service::models::NotConnected;

enum ConnectionState {
    // Newly connecting or reconnecting due to an error.
    Connecting(Option<anyhow::Error>),

    Connected,

    // Disconnected, which may be a result of an error.
    Disconnected(Option<ClientError>),
}

impl ConnectionState {
    pub fn init_failed(e: anyhow::Error) -> Self {
        ConnectionState::Disconnected(Some(ClientError::InitFailed(e)))
    }

    pub fn stream_failed(e: anyhow::Error) -> Self {
        ConnectionState::Disconnected(Some(ClientError::StreamFailed(e)))
    }

    pub fn event_handling_failed(e: anyhow::Error) -> Self {
        ConnectionState::Disconnected(Some(ClientError::EventHandlingFailed(e)))
    }

    pub fn not_connected(&self) -> Option<NotConnected> {
        match self {
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

enum ClientError {
    InitFailed(anyhow::Error),
    StreamFailed(anyhow::Error),
    EventHandlingFailed(anyhow::Error),
}
