//! Server-Sent Events client of Node API Service.

use std::{ops::DerefMut, sync::Arc};

use anyhow::{bail, Result};
use bytes::Bytes;
use cotoami_db::{ChangelogEntry, Id, Node, Operator};
use futures::StreamExt;
use parking_lot::{Mutex, RwLock};
use reqwest_eventsource::{Event as ESItem, EventSource, ReadyState};
use tokio::task::{spawn_blocking, AbortHandle, JoinSet};
use tower_service::Service;
use tracing::{debug, error, info};

use crate::{
    client::{ConnectionState, HttpClient},
    event::NodeSentEvent,
    service::{models::NotConnected, Request, Response},
    state::NodeState,
};

/////////////////////////////////////////////////////////////////////////////
// SseClient
/////////////////////////////////////////////////////////////////////////////

/// An [SseClient] handles events streamed from an [EventSource].
#[derive(Clone)]
pub struct SseClient {
    state: Arc<State>,
}

struct State {
    server_id: Id<Node>,
    server_as_operator: Option<Arc<Operator>>,
    http_client: HttpClient,
    conn_state: RwLock<ConnectionState>,
    node_state: NodeState,
    abortables: Mutex<Vec<AbortHandle>>,
}

impl SseClient {
    pub async fn new(
        server_id: Id<Node>,
        http_client: HttpClient,
        node_state: NodeState,
    ) -> Result<Self> {
        let server_as_operator = spawn_blocking({
            let db = node_state.db().clone();
            move || db.new_session()?.as_operator(server_id)
        })
        .await??
        .map(Arc::new);

        let state = State {
            server_id,
            server_as_operator,
            http_client,
            conn_state: RwLock::new(ConnectionState::Disconnected(None)),
            node_state,
            abortables: Mutex::new(Vec::new()),
        };
        Ok(Self {
            state: Arc::new(state),
        })
    }

    pub fn server_id(&self) -> &Id<Node> { &self.state.server_id }

    pub fn http_client(&self) -> &HttpClient { &self.state.http_client }

    pub fn url_prefix(&self) -> &str { self.http_client().url_prefix() }

    pub fn not_connected(&self) -> Option<NotConnected> {
        self.state.conn_state.read().not_connected()
    }

    fn server_as_operator(&self) -> Option<&Arc<Operator>> {
        self.state.server_as_operator.as_ref()
    }

    fn node_state(&self) -> &NodeState { &self.state.node_state }

    fn is_server_parent(&self) -> bool { self.node_state().is_parent(&self.state.server_id) }

    fn set_conn_state(&self, state: ConnectionState) {
        let _ = std::mem::replace(self.state.conn_state.write().deref_mut(), state);
    }

    fn has_running_tasks(&self) -> bool { !self.state.abortables.lock().is_empty() }

    fn add_abortable(&self, abortable: AbortHandle) {
        self.state.abortables.lock().push(abortable);
    }

    fn new_event_source(&self) -> Result<EventSource> {
        // To inherit request headers (ex. session token) from the `http_client`,
        // an event source has to be constructed via [EventSource::new] with a
        // [RequestBuilder] constructed by the `http_client`.
        EventSource::new(self.http_client().get("/api/events", None)).map_err(anyhow::Error::from)
    }

    pub fn connect(&mut self) {
        if self.has_running_tasks() {
            return;
        }
        match self.new_event_source() {
            Err(e) => {
                self.set_conn_state(ConnectionState::init_failed(e));
            }
            Ok(event_source) => {
                let this = self.clone();
                tokio::spawn(async move {
                    let mut tasks = JoinSet::new();

                    // A task: event_loop
                    this.add_abortable(tasks.spawn(this.clone().event_loop(event_source)));

                    // A task: stream_changes_to_server
                    if !this.is_server_parent() {
                        this.add_abortable(tasks.spawn(this.clone().stream_changes_to_server()));
                    }

                    // If any one of the tasks exit, abort the others.
                    if let Some(_) = tasks.join_next().await {
                        tasks.shutdown().await;
                    }
                });
            }
        }
    }

    pub fn disconnect(&mut self) {
        info!("Disconnecting from: {}", self.url_prefix());
        let mut abortables = self.state.abortables.lock();
        while let Some(abortable) = abortables.pop() {
            abortable.abort();
        }
        self.set_conn_state(ConnectionState::Disconnected(None));
        self.publish_server_disconnected();
    }

    async fn event_loop(mut self, mut event_source: EventSource) {
        while let Some(item) = event_source.next().await {
            match item {
                Ok(ESItem::Open) => {
                    info!("Event source opened: {}", self.url_prefix());
                    self.set_conn_state(ConnectionState::Connected);

                    // Server-as-parent
                    if self.is_server_parent() {
                        self.node_state().put_parent_service(
                            *self.server_id(),
                            Box::new(self.http_client().clone()),
                        );
                    // Server-as-child
                    } else {
                        if let Err(e) = self
                            .http_client()
                            .post_event(&NodeSentEvent::Connected)
                            .await
                        {
                            event_source.close();
                            self.set_conn_state(ConnectionState::init_failed(e));
                            break;
                        }
                    }
                }
                Ok(ESItem::Message(event)) => {
                    if let Err(e) = self.handle_node_sent_event(event.into()).await {
                        debug!(
                            "Event source {} closed because of an event handling error: {}",
                            self.url_prefix(),
                            &e
                        );
                        event_source.close();
                        self.set_conn_state(ConnectionState::event_handling_failed(e));
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
                        self.set_conn_state(ConnectionState::stream_failed(e.into()));
                        break;
                    } else {
                        debug!(
                            "Reconnecting to {} after an error: {:?}",
                            self.url_prefix(),
                            &e
                        );
                        self.set_conn_state(ConnectionState::Connecting(Some(e.into())));
                    }
                }
            }
        }
        self.publish_server_disconnected();
    }

    async fn stream_changes_to_server(self) {
        let mut changes = self
            .node_state()
            .pubsub()
            .local_changes()
            .subscribe(None::<()>);
        while let Some(change) = changes.next().await {
            if let Err(e) = self
                .http_client()
                .post_event(&NodeSentEvent::Change(change.clone()))
                .await
            {
                // This error won't stop this task as `event_loop` takes
                // responsibility for maintaining the connection.
                error!("Error sending a change to child servers: {e}");
            }
        }
    }

    fn publish_server_disconnected(&self) {
        if let Some(not_connected) = self.not_connected() {
            self.node_state()
                .pubsub()
                .events()
                .publish_server_disconnected(
                    *self.server_id(),
                    not_connected,
                    self.is_server_parent(),
                );
        }
    }

    async fn handle_node_sent_event(&mut self, event: NodeSentEvent) -> Result<()> {
        // Server-as-child
        if let Some(opr) = self.server_as_operator() {
            match event {
                NodeSentEvent::Request(mut request) => {
                    debug!("Received a request from {}: {request:?}", self.url_prefix());
                    request.set_from(opr.clone());

                    // Since [tower_service::Service::call] requires a mutable reference of self
                    // (it doesn't have to be mutable actually because the inner state is wrapped in [Arc]),
                    // here it clones the [NodeState] to make it mutable.
                    let mut node_state = self.node_state().clone();
                    let response = node_state.call(request).await?;

                    self.http_client()
                        .post_event(&NodeSentEvent::Response(response))
                        .await?;
                }
                NodeSentEvent::Error(msg) => error!("Event error: {msg}"),
                unsupported => {
                    info!("SSE client-as-parent doesn't support the event: {unsupported:?}");
                }
            }
        // Server-as-parent
        } else {
            match event {
                NodeSentEvent::Change(change) => {
                    // `sync_with_parent` can't be run in parallel since events from the
                    // same node will be handled one by one.
                    self.node_state()
                        .handle_parent_change(
                            *self.server_id(),
                            change,
                            Box::new(self.http_client().clone()),
                        )
                        .await?;
                }
                NodeSentEvent::Error(msg) => error!("Event error: {msg}"),
                unsupported => {
                    info!("SSE client-as-child doesn't support the event: {unsupported:?}");
                }
            }
        }
        Ok(())
    }
}

/////////////////////////////////////////////////////////////////////////////
// NodeSentEvent
/////////////////////////////////////////////////////////////////////////////

impl From<eventsource_stream::Event> for NodeSentEvent {
    fn from(source: eventsource_stream::Event) -> Self {
        match &*source.event {
            "change" => match serde_json::from_str::<ChangelogEntry>(&source.data) {
                Ok(change) => NodeSentEvent::Change(change),
                Err(e) => NodeSentEvent::Error(e.to_string()),
            },
            "request" => match serde_json::from_str::<Request>(&source.data) {
                Ok(request) => NodeSentEvent::Request(request),
                Err(e) => NodeSentEvent::Error(e.to_string()),
            },
            "response" => match serde_json::from_str::<Response>(&source.data) {
                Ok(response) => NodeSentEvent::Response(response),
                Err(e) => NodeSentEvent::Error(e.to_string()),
            },
            "error" => NodeSentEvent::Error(source.data),
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
    pub(crate) async fn post_event(&self, event: &NodeSentEvent) -> Result<()> {
        let bytes = rmp_serde::to_vec(event).map(Bytes::from)?;
        let response = self.post("/api/events").body(bytes).send().await?;
        if response.status().is_success() {
            Ok(())
        } else {
            bail!(response.text().await?);
        }
    }
}
