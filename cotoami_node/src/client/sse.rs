//! Server-Sent Events client of Node API Service.

use std::{ops::ControlFlow, sync::Arc};

use anyhow::{bail, Result};
use bytes::Bytes;
use cotoami_db::{ChangelogEntry, Id, Node};
use futures::{sink::Sink, StreamExt};
use reqwest_eventsource::{Event as ESItem, EventSource, ReadyState};
use tokio::task::JoinSet;
use tracing::{debug, error, info};

use crate::{
    client::{ClientState, ConnectionState, HttpClient},
    event::{
        local::LocalNodeEvent,
        remote::{handle_event_from_operator, handle_event_from_parent, NodeSentEvent},
    },
    service::{models::NotConnected, Request, Response},
    state::NodeState,
};

/////////////////////////////////////////////////////////////////////////////
// SseClient
/////////////////////////////////////////////////////////////////////////////

/// An [SseClient] handles events streamed from an [EventSource].
#[derive(Clone)]
pub struct SseClient {
    state: Arc<ClientState>,
    http_client: HttpClient,
}

impl SseClient {
    pub async fn new(
        server_id: Id<Node>,
        http_client: HttpClient,
        node_state: NodeState,
    ) -> Result<Self> {
        let state = ClientState::new(server_id, node_state).await?;
        Ok(Self {
            state: Arc::new(state),
            http_client,
        })
    }

    pub fn not_connected(&self) -> Option<NotConnected> { self.state.not_connected() }

    fn url_prefix(&self) -> &str { self.http_client.url_prefix().as_str() }

    fn node_state(&self) -> &NodeState { &self.state.node_state }

    fn new_event_source(&self) -> EventSource {
        // To inherit request headers (ex. session token) from the `http_client`,
        // an event source has to be constructed via [EventSource::new] with a
        // [RequestBuilder] constructed by the `http_client`.
        EventSource::new(self.http_client.get("/api/events")).unwrap_or_else(|_| unreachable!())
    }

    pub fn connect(&mut self) {
        if self.state.has_running_tasks() {
            return;
        }
        tokio::spawn({
            let this = self.clone();
            async move {
                let event_source = this.new_event_source();
                let mut tasks = JoinSet::new();

                // A task: event_loop
                this.state
                    .abortables
                    .add(tasks.spawn(this.clone().event_loop(event_source)));

                // A task: stream_changes_to_server
                if !this.state.is_server_parent() {
                    this.state
                        .abortables
                        .add(tasks.spawn(this.clone().stream_changes_to_server()));
                }

                // If any one of the tasks exit, abort the others.
                if tasks.join_next().await.is_some() {
                    tasks.shutdown().await;
                }
            }
        });
    }

    pub fn disconnect(&mut self) { self.state.disconnect(); }

    async fn event_loop(mut self, mut event_source: EventSource) {
        while let Some(item) = event_source.next().await {
            match item {
                Ok(ESItem::Open) => {
                    info!("Event source opened: {}", self.url_prefix());
                    self.state.change_conn_state(ConnectionState::Connected);
                    if self.state.is_server_parent() {
                        let parent_service = Box::new(self.http_client.clone());
                        self.node_state()
                            .register_parent_service(self.state.server_id, parent_service);
                    }
                }
                Ok(ESItem::Message(event)) => {
                    if let ControlFlow::Break(e) = self.handle_node_sent_event(event.into()).await {
                        debug!(
                            "Event source {} closed because of an event handling error: {}",
                            self.url_prefix(),
                            &e
                        );
                        event_source.close();
                        self.state
                            .change_conn_state(ConnectionState::event_handling_failed(e));
                        break;
                    }
                }
                Err(e) => {
                    if event_source.ready_state() == ReadyState::Closed {
                        debug!(
                            "Event source {} closed because of a stream error: {:?}",
                            self.url_prefix(),
                            &e
                        );
                        self.state
                            .change_conn_state(ConnectionState::communication_failed(e.into()));
                        break;
                    } else {
                        debug!(
                            "Reconnecting to {} after an error: {:?}",
                            self.url_prefix(),
                            &e
                        );
                        self.state
                            .change_conn_state(ConnectionState::Connecting(Some(e.into())));
                    }
                }
            }
        }
        self.state
            .change_conn_state(ConnectionState::Disconnected(None));
    }

    async fn stream_changes_to_server(self) {
        let mut changes = self.node_state().pubsub().changes().subscribe(None::<()>);
        while let Some(change) = changes.next().await {
            if let Err(e) = self
                .http_client
                .post_event(NodeSentEvent::Change(change.clone()))
                .await
            {
                // This error won't stop this task as `event_loop` takes
                // responsibility for maintaining the connection.
                error!("Error sending a change to child servers: {e}");
            }
        }
    }

    async fn handle_node_sent_event(&mut self, event: NodeSentEvent) -> ControlFlow<anyhow::Error> {
        if let Some(opr) = self.state.server_as_operator.as_ref() {
            let sink = self.http_client.as_event_sink();
            futures::pin_mut!(sink);
            handle_event_from_operator(event, opr.clone(), self.node_state().clone(), sink).await
        } else {
            handle_event_from_parent(event, self.state.server_id, self.node_state().clone()).await
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// NodeSentEvent
/////////////////////////////////////////////////////////////////////////////

impl From<eventsource_stream::Event> for NodeSentEvent {
    fn from(source: eventsource_stream::Event) -> Self {
        match &*source.event {
            Self::NAME_CHANGE => match serde_json::from_str::<ChangelogEntry>(&source.data) {
                Ok(change) => NodeSentEvent::Change(change),
                Err(e) => NodeSentEvent::Error(e.to_string()),
            },
            Self::NAME_REQUEST => match serde_json::from_str::<Request>(&source.data) {
                Ok(request) => NodeSentEvent::Request(request),
                Err(e) => NodeSentEvent::Error(e.to_string()),
            },
            Self::NAME_RESPONSE => match serde_json::from_str::<Response>(&source.data) {
                Ok(response) => NodeSentEvent::Response(response),
                Err(e) => NodeSentEvent::Error(e.to_string()),
            },
            Self::NAME_REMOTE_LOCAL => match serde_json::from_str::<LocalNodeEvent>(&source.data) {
                Ok(event) => NodeSentEvent::RemoteLocal(event),
                Err(e) => NodeSentEvent::Error(e.to_string()),
            },
            Self::NAME_ERROR => NodeSentEvent::Error(source.data),
            _ => NodeSentEvent::Error(format!("Unknown event: {}", source.event)),
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// ReadyState -> ConnectionState
/////////////////////////////////////////////////////////////////////////////

impl From<ReadyState> for ConnectionState {
    fn from(src: ReadyState) -> Self {
        match src {
            ReadyState::Connecting => Self::Connecting(None),
            ReadyState::Open => Self::Connected,
            ReadyState::Closed => Self::Disconnected(None),
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/events
/////////////////////////////////////////////////////////////////////////////

impl HttpClient {
    pub(crate) async fn post_event(&self, event: NodeSentEvent) -> Result<()> {
        let bytes = rmp_serde::to_vec(&event).map(Bytes::from)?;
        let response = self.post("/api/events").body(bytes).send().await?;
        if response.status().is_success() {
            Ok(())
        } else {
            bail!(response.text().await?);
        }
    }

    pub(crate) fn as_event_sink(&self) -> impl Sink<NodeSentEvent, Error = anyhow::Error> + '_ {
        futures::sink::unfold((), |(), event: NodeSentEvent| self.post_event(event))
    }
}
