use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use futures::StreamExt;
use parking_lot::RwLock;
use reqwest_eventsource::{Event as ESItem, EventSource, ReadyState};
use tracing::{debug, info};

use crate::{client::HttpClient, service::models::NotConnected, NodeState};

/// An [SseClient] handles events streamed from an [EventSource].
pub struct SseClient {
    server_node_id: Id<Node>,

    http_client: HttpClient,
    event_source: EventSource,
    state: Arc<RwLock<SseClientState>>,

    node_state: NodeState,
}

impl SseClient {
    pub fn new(
        server_node_id: Id<Node>,
        http_client: HttpClient,
        node_state: NodeState,
    ) -> Result<Self> {
        // To inherit request headers (ex. session token) from the `http_client`,
        // an event source has to be constructed via [EventSource::new] with a
        // [RequestBuilder] constructed by the `http_client`.
        let event_source = EventSource::new(http_client.get("/api/events", None))?;
        let state = SseClientState::new(event_source.ready_state());
        Ok(Self {
            server_node_id,
            http_client,
            event_source,
            state: Arc::new(RwLock::new(state)),
            node_state,
        })
    }

    pub fn state(&self) -> Arc<RwLock<SseClientState>> { self.state.clone() }

    pub fn is_disabled(&self) -> bool { self.state.read().is_disabled() }

    pub fn url_prefix(&self) -> &str { self.http_client.url_prefix() }

    pub async fn start(&mut self) {
        while let Some(item) = self.event_source.next().await {
            if self.is_disabled() {
                self.event_source.close();
                info!("Event source closed: {}", self.url_prefix());
            } else {
                match item {
                    Ok(ESItem::Open) => info!("Event source opened: {}", self.url_prefix()),
                    Ok(ESItem::Message(event)) => {
                        if let Err(err) = self
                            .node_state
                            .handle_event(
                                self.server_node_id,
                                event.into(),
                                Box::new(self.http_client.clone()),
                            )
                            .await
                        {
                            debug!(
                                "Event source {} closed because of an event handling error: {}",
                                self.url_prefix(),
                                &err
                            );
                            self.set_error(SseClientError::EventHandlingFailed(err));
                            self.event_source.close();
                        }
                    }
                    Err(err) => {
                        if self.event_source.ready_state() == ReadyState::Closed {
                            debug!(
                                "Event source {} closed because of a stream error: {:?}",
                                self.url_prefix(),
                                &err
                            );
                        } else {
                            debug!(
                                "Reconnecting to {} after an error: {:?}",
                                self.url_prefix(),
                                &err
                            )
                        }
                        self.set_error(SseClientError::StreamFailed(err));
                    }
                }
            }
            self.update_event_source_state();
        }
        // After the end of the stream
        self.update_event_source_state();
    }

    fn update_event_source_state(&mut self) {
        let mut state = self.state.write();
        state.event_source_state = self.event_source.ready_state();
    }

    fn set_error(&mut self, error: SseClientError) {
        let mut state = self.state.write();
        state.error = Some(error);
    }
}

/// The state of an [SseClient] that can be shared between threads.
///
/// An [SseClient] has an [EventSource] as its state, but it is not [Sync],
/// so this struct is needed to put the state in the global state.
pub struct SseClientState {
    pub event_source_state: ReadyState,
    pub error: Option<SseClientError>,
    disabled: bool,
}

impl SseClientState {
    fn new(event_source_state: ReadyState) -> Self {
        Self {
            event_source_state,
            error: None,
            disabled: false,
        }
    }

    /// Disable this event loop. A disabled loop will close the event source when
    /// the next event comes (this event will be ignored). In other words, the event
    /// source will never be closed if no events come in the loop.
    pub fn disable(&mut self) { self.disabled = true; }

    pub fn is_disabled(&self) -> bool { self.disabled }

    /// Returns true if this event loop is accepting events.
    pub fn is_running(&self) -> bool {
        !self.disabled && self.event_source_state == ReadyState::Open
    }

    /// Returns true if the [EventSource] is waiting on a response from the endpoint
    pub fn is_connecting(&self) -> bool { self.event_source_state == ReadyState::Connecting }

    pub fn not_connected(&self) -> Option<NotConnected> {
        if self.is_running() {
            None // connected
        } else if self.is_disabled() {
            Some(NotConnected::Disabled)
        } else if self.is_connecting() {
            let details = if let Some(SseClientError::StreamFailed(e)) = self.error.as_ref() {
                Some(e.to_string())
            } else {
                None
            };
            Some(NotConnected::Connecting(details))
        } else if let Some(error) = self.error.as_ref() {
            match error {
                SseClientError::StreamFailed(e) => Some(NotConnected::StreamFailed(e.to_string())),
                SseClientError::EventHandlingFailed(e) => {
                    Some(NotConnected::EventHandlingFailed(e.to_string()))
                }
            }
        } else {
            Some(NotConnected::Unknown)
        }
    }

    /// Enable this event loop only if the event source is not closed.
    /// It returns true if the result state of the event loop is `running`
    /// (enabled and connected) or `connecting`.
    pub fn restart_if_possible(&mut self) -> bool {
        if self.event_source_state != ReadyState::Closed {
            self.disabled = false;
            true
        } else {
            false
        }
    }
}

pub enum SseClientError {
    StreamFailed(reqwest_eventsource::Error),
    EventHandlingFailed(anyhow::Error),
}
