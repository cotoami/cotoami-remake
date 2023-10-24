use std::{borrow::Cow, sync::Arc};

use anyhow::{bail, Result};
use cotoami_db::prelude::*;
use eventsource_stream::Event;
use futures::StreamExt;
use parking_lot::{Mutex, RwLock};
use reqwest::{
    header::{HeaderMap, HeaderValue},
    Client, IntoUrl, RequestBuilder, Response, Url,
};
use reqwest_eventsource::{Event as ESItem, EventSource, ReadyState};
use thiserror::Error;
use tokio::task::spawn_blocking;
use tracing::{debug, info};

use crate::{
    api::{
        changes::ChangesResult,
        session::{ChildSessionCreated, CreateChildSession},
        SESSION_HEADER_NAME,
    },
    csrf, ChangePub, Pubsub,
};

/////////////////////////////////////////////////////////////////////////////
// Server
/////////////////////////////////////////////////////////////////////////////

#[derive(Clone)]
pub(crate) struct Server {
    client: Client,
    url_prefix: String,
    headers: HeaderMap,
}

impl Server {
    pub fn new(url_prefix: String) -> Result<Self> {
        let client = Client::builder()
            .default_headers(Self::default_headers())
            .build()?;
        Ok(Self {
            client,
            url_prefix,
            headers: HeaderMap::new(),
        })
    }

    pub fn url_prefix(&self) -> &str { &self.url_prefix }

    pub fn set_session_token(&mut self, token: &str) -> Result<()> {
        let mut token = HeaderValue::from_str(token)?;
        token.set_sensitive(true);
        self.headers.insert(SESSION_HEADER_NAME, token);
        Ok(())
    }

    /// Creates a new child session via `/api/session/child`.
    ///
    /// If it succeeds to create a session, the session token will be saved in
    /// this client and used as a request header in subsequent requests.
    pub async fn create_child_session(
        &mut self,
        password: String,
        new_password: Option<String>,
        child: &Node,
    ) -> Result<ChildSessionCreated> {
        let url = self.make_url("/api/session/child", None)?;
        let req_body = CreateChildSession {
            password,
            new_password,
            child: Cow::Borrowed(child),
        };
        let response = self.put(url).json(&req_body).send().await?;
        if response.status() != reqwest::StatusCode::CREATED {
            return Self::into_err(response).await?;
        }
        let child_session = response.json::<ChildSessionCreated>().await?;
        self.set_session_token(&child_session.session.token)?;
        Ok(child_session)
    }

    pub async fn chunk_of_changes(&self, from: i64) -> Result<ChangesResult> {
        let url = self.make_url("/api/changes", Some(vec![("from", &from.to_string())]))?;
        let response = self.get(url).send().await?;
        if response.status() != reqwest::StatusCode::OK {
            return Self::into_err(response).await?;
        }
        Ok(response.json::<ChangesResult>().await?)
    }

    pub async fn import_changes(
        &self,
        db: &Arc<Database>,
        pubsub: &Arc<Mutex<Pubsub>>,
        parent_node_id: Id<Node>,
    ) -> Result<Option<(i64, i64)>> {
        info!("Importing the changes from {}", self.url_prefix());
        let parent_node = {
            let mut db = db.new_session()?;
            let opr = db.local_node_as_operator()?;
            db.parent_node_ext(&parent_node_id, &opr)?
        };
        let import_from = parent_node.changes_received + 1;
        let mut from = import_from;
        loop {
            // Get a chunk of changelog entries from the server
            let changes = match self.chunk_of_changes(from).await? {
                ChangesResult::Fetched(changes) => changes,
                ChangesResult::OutOfRange { max } => {
                    if from == import_from && parent_node.changes_received == max {
                        // A case where the local has already synced with the parent
                        info!("Already synced with: {}", self.url_prefix());
                        return Ok(None);
                    } else {
                        // The number of `parent_node.changes_received` is larger than
                        // the last number of the changes in the parent node for some reason.
                        // That means the replication has broken between the two nodes.
                        bail!(
                            "Tried to import from {}, but the last change number was {}.",
                            from,
                            max
                        );
                    }
                }
            };
            let is_last_chunk = changes.is_last_chunk();
            let last_number_of_chunk = changes.last_serial_number_of_chunk();

            debug!(
                "Fetched a chunk of changes: {}-{} (is_last: {}, max: {})",
                from, last_number_of_chunk, is_last_chunk, changes.last_serial_number
            );

            // Import the changes to the local database
            let (db, pubsub) = (db.clone(), pubsub.clone());
            let chunk_imported: Result<()> = spawn_blocking(move || {
                let db = db.new_session()?;
                for change in changes.chunk {
                    debug!("Importing number {} ...", change.serial_number);
                    if let Some(imported_change) = db.import_change(&change, &parent_node_id)? {
                        pubsub.lock().publish_change(imported_change)?;
                    }
                }
                Ok(())
            })
            .await?;
            chunk_imported?;

            // Next chunk or finish import
            if is_last_chunk {
                info!(
                    "Imported changes {}-{} from {}",
                    import_from,
                    last_number_of_chunk,
                    self.url_prefix()
                );
                return Ok(Some((import_from, last_number_of_chunk)));
            } else {
                from = last_number_of_chunk + 1;
            }
        }
    }

    pub async fn create_event_loop(
        &self,
        parent_node_id: Id<Node>,
        db: &Arc<Database>,
        pubsub: &Arc<Mutex<Pubsub>>,
    ) -> Result<EventLoop> {
        EventLoop::new(self.clone(), parent_node_id, db, pubsub)
    }

    fn default_headers() -> HeaderMap {
        let mut headers = HeaderMap::new();
        headers.insert(
            csrf::CUSTOM_HEADER,
            HeaderValue::from_static("cotoami_node"),
        );
        headers
    }

    fn make_url(&self, path: &str, query: Option<Vec<(&str, &str)>>) -> Result<Url> {
        let mut url = Url::parse(&self.url_prefix)?.join(path)?;
        if let Some(query) = query {
            let mut pairs = url.query_pairs_mut();
            for (name, value) in query.iter() {
                pairs.append_pair(name, value);
            }
        }
        Ok(url)
    }

    fn get<U: IntoUrl>(&self, url: U) -> RequestBuilder {
        self.client.get(url).headers(self.headers.clone())
    }

    fn put<U: IntoUrl>(&self, url: U) -> RequestBuilder {
        self.client.put(url).headers(self.headers.clone())
    }

    async fn into_err<T>(response: Response) -> Result<T, ResponseError> {
        Err(ResponseError {
            url: response.url().to_string(),
            status: response.status().as_u16(),
            body: response.text().await.unwrap_or_else(|e| e.to_string()),
        })
    }
}

#[derive(Error, Debug)]
#[error("Error response ({status}) from {url}")]
pub(crate) struct ResponseError {
    pub url: String,
    pub status: u16,
    pub body: String,
}

/////////////////////////////////////////////////////////////////////////////
// EventLoop
/////////////////////////////////////////////////////////////////////////////

/// An [EventLoop] handles events streamed from an [EventSource].
pub(crate) struct EventLoop {
    server: Server,
    parent_node_id: Id<Node>,
    db: Arc<Database>,
    pubsub: Arc<Mutex<Pubsub>>,
    event_source: EventSource,
    state: Arc<RwLock<EventLoopState>>,
}

impl EventLoop {
    fn new(
        server: Server,
        parent_node_id: Id<Node>,
        db: &Arc<Database>,
        pubsub: &Arc<Mutex<Pubsub>>,
    ) -> Result<Self> {
        let url = server.make_url("/api/events", None)?;
        // To inherit request headers (ex. session token) from the `server`,
        // an event source has to be constructed via [EventSource::new] with a
        // [RequestBuilder] constructed by the `server`.
        let event_source = EventSource::new(server.get(url))?;
        let state = EventLoopState::new(event_source.ready_state());
        Ok(Self {
            server,
            parent_node_id,
            db: db.clone(),
            pubsub: pubsub.clone(),
            event_source,
            state: Arc::new(RwLock::new(state)),
        })
    }

    pub fn state(&self) -> Arc<RwLock<EventLoopState>> { self.state.clone() }

    pub async fn start(&mut self) {
        while let Some(item) = self.event_source.next().await {
            if self.is_disabled() {
                self.event_source.close();
                info!("Event source closed: {}", self.server.url_prefix());
            } else {
                match item {
                    Ok(ESItem::Open) => info!("Event source opened: {}", self.server.url_prefix()),
                    Ok(ESItem::Message(event)) => {
                        if let Err(err) = self.handle_event(&event).await {
                            debug!(
                                "Event source {} closed because of an event handling error: {}",
                                self.server.url_prefix(),
                                &err
                            );
                            self.set_error(EventLoopError::EventHandlingFailed(err));
                            self.event_source.close();
                        }
                    }
                    Err(err) => {
                        if self.event_source.ready_state() == ReadyState::Closed {
                            debug!(
                                "Event source {} closed because of a stream error: {:?}",
                                self.server.url_prefix(),
                                &err
                            );
                        } else {
                            debug!(
                                "Reconnecting to {} after an error: {:?}",
                                self.server.url_prefix(),
                                &err
                            )
                        }
                        self.set_error(EventLoopError::StreamFailed(err));
                    }
                }
            }
            self.update_event_source_state();
        }
        // After the end of the stream
        self.update_event_source_state();
    }

    fn is_disabled(&self) -> bool { self.state.read().is_disabled() }

    fn update_event_source_state(&mut self) {
        let mut state = self.state.write();
        state.event_source_state = self.event_source.ready_state();
    }

    fn set_error(&mut self, error: EventLoopError) {
        let mut state = self.state.write();
        state.error = Some(error);
    }

    async fn handle_event(&mut self, event: &Event) -> Result<()> {
        debug!("Handling a server event: {:?}", event);

        let change = serde_json::from_str::<ChangelogEntry>(&event.data)?;
        info!(
            "Received a change {} from {}",
            change.serial_number,
            self.server.url_prefix()
        );

        let db = self.db.clone();
        let parent_node_id = self.parent_node_id;
        let import_result: Result<Option<ChangelogEntry>> =
            spawn_blocking(move || db.new_session()?.import_change(&change, &parent_node_id))
                .await?;
        match import_result {
            Err(anyhow_err) => {
                if let Some(DatabaseError::UnexpectedChangeNumber {
                    expected, actual, ..
                }) = anyhow_err.downcast_ref::<DatabaseError>()
                {
                    info!(
                        "Out of sync with {} (received: {}, expected {})",
                        self.server.url_prefix(),
                        actual,
                        expected,
                    );
                    self.server
                        .import_changes(&self.db, &self.pubsub, self.parent_node_id)
                        .await?;
                } else {
                    return Err(anyhow_err);
                }
            }
            Ok(Some(imported_change)) => {
                self.pubsub.lock().publish_change(imported_change)?;
            }
            Ok(None) => (),
        }
        Ok(())
    }
}

/// The state of an [EventLoop] that can be shared between threads.
///
/// An [EventLoop] has an [EventSource] as its state, but it is not [Sync]. That's
/// why this struct is needed to put the state in the global state.
pub(crate) struct EventLoopState {
    pub event_source_state: ReadyState,
    pub error: Option<EventLoopError>,
    disabled: bool,
}

impl EventLoopState {
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

pub(crate) enum EventLoopError {
    StreamFailed(reqwest_eventsource::Error),
    EventHandlingFailed(anyhow::Error),
}
