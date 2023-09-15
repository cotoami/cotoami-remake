use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use eventsource_stream::Event;
use futures::StreamExt;
use parking_lot::{Mutex, RwLock};
use reqwest::{
    header::{HeaderMap, HeaderValue},
    Client, Response, Url,
};
use reqwest_eventsource::{Event as ESItem, EventSource, ReadyState};
use thiserror::Error;
use tokio::task::spawn_blocking;
use tracing::{debug, info};

use crate::{
    api::{
        changes::Changes,
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
}

impl Server {
    pub fn new(url_prefix: String, session_token: Option<&str>) -> Result<Self> {
        // Default request headers
        let mut headers = HeaderMap::new();
        headers.insert(
            csrf::CUSTOM_HEADER,
            HeaderValue::from_static("cotoami_node"),
        );
        if let Some(token) = session_token {
            let mut token = HeaderValue::from_str(token)?;
            token.set_sensitive(true);
            headers.insert(SESSION_HEADER_NAME, token);
        };

        let client = Client::builder().default_headers(headers).build()?;
        Ok(Self { client, url_prefix })
    }

    pub fn url_prefix(&self) -> &str { &self.url_prefix }

    pub async fn create_child_session(
        &self,
        password: String,
        new_password: Option<String>,
        child: Node,
    ) -> Result<ChildSessionCreated> {
        let url = self.make_url("/api/session/child")?;
        let req_body = CreateChildSession {
            password,
            new_password,
            child,
        };
        let response = self.client.put(url).json(&req_body).send().await?;
        if response.status() != reqwest::StatusCode::CREATED {
            return Self::into_err(response).await?;
        }
        Ok(response.json::<ChildSessionCreated>().await?)
    }

    pub async fn chunk_of_changes(&self, from: i64) -> Result<Changes> {
        let mut url = self.make_url("/api/changes")?;
        url.query_pairs_mut().append_pair("from", &from.to_string());
        let response = self.client.get(url).send().await?;
        if response.status() != reqwest::StatusCode::OK {
            return Self::into_err(response).await?;
        }
        Ok(response.json::<Changes>().await?)
    }

    pub async fn import_changes(
        &self,
        db: Arc<Database>,
        pubsub: Arc<Mutex<Pubsub>>,
        parent_node_id: Id<Node>,
    ) -> Result<(i64, i64)> {
        let parent_node = db.create_session()?.parent_node_or_err(&parent_node_id)?;
        let import_from = parent_node.changes_received + 1;
        debug!("import_changes from {}", import_from);
        let mut from = import_from;
        loop {
            debug!("Importing a chunk of changes from {}", from);

            // Get a chunk of changelog entries from the server
            let changes = self.chunk_of_changes(from).await?;
            let is_last_chunk = changes.is_last_chunk();
            let last_number_of_chunk = changes.last_serial_number_of_chunk();

            // Import the changes to the local database
            let db = db.clone();
            let pubsub = pubsub.clone();
            let chunk_imported: Result<()> = spawn_blocking(move || {
                let db = db.create_session()?;
                for change in changes.chunk {
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
                return Ok((import_from, last_number_of_chunk));
            } else {
                from = last_number_of_chunk + 1;
            }
        }
    }

    pub async fn create_event_loop(
        &self,
        parent_node_id: Id<Node>,
        db: Arc<Database>,
        pubsub: Arc<Mutex<Pubsub>>,
    ) -> Result<EventLoop> {
        EventLoop::new(self.clone(), parent_node_id, db, pubsub)
    }

    fn make_url(&self, path: &str) -> Result<Url> { Ok(Url::parse(&self.url_prefix)?.join(path)?) }

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

pub(crate) struct EventLoopState {
    ready_state: ReadyState,
    error: Option<anyhow::Error>,
    end_loop: bool,
}

impl EventLoopState {
    fn new(ready_state: ReadyState) -> Self {
        Self {
            ready_state,
            error: None,
            end_loop: false,
        }
    }

    pub fn end(&mut self) { self.end_loop = true; }
}

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
        db: Arc<Database>,
        pubsub: Arc<Mutex<Pubsub>>,
    ) -> Result<Self> {
        let url = server.make_url("/api/events")?;
        let event_source = EventSource::get(url);
        let state = EventLoopState::new(event_source.ready_state());
        Ok(Self {
            server,
            parent_node_id,
            db,
            pubsub,
            event_source,
            state: Arc::new(RwLock::new(state)),
        })
    }

    fn set_ready_state(&mut self, ready_state: ReadyState) {
        self.state.write().ready_state = ready_state;
    }

    fn set_error(&mut self, error: anyhow::Error) { self.state.write().error = Some(error); }

    pub fn state(&self) -> Arc<RwLock<EventLoopState>> { self.state.clone() }

    pub async fn start(&mut self) {
        while let Some(item) = self.event_source.next().await {
            match item {
                Ok(ESItem::Open) => info!("Event stream opened: {}", self.server.url_prefix()),
                Ok(ESItem::Message(event)) => {
                    if let Err(err) = self.handle_event(&event).await {
                        debug!(
                            "Event stream {} closed because of an error in handling an event: {}",
                            self.server.url_prefix(),
                            &err
                        );
                        self.event_source.close();
                        self.set_error(anyhow::Error::from(err));
                    }
                }
                Err(err) => {
                    debug!(
                        "Event stream {} closed because of a stream error: {}",
                        self.server.url_prefix(),
                        &err
                    );
                    self.event_source.close();
                    self.set_error(anyhow::Error::from(err));
                }
            }

            if self.state.read().end_loop {
                self.event_source.close();
                info!("Event stream closed: {}", self.server.url_prefix());
            }
            self.set_ready_state(self.event_source.ready_state());
        }
    }

    async fn handle_event(&mut self, event: &Event) -> Result<()> {
        let change = serde_json::from_str::<ChangelogEntry>(&event.data)?;
        let db = self.db.clone();
        let parent_node_id = self.parent_node_id;
        let import_result: Result<Option<ChangelogEntry>> =
            spawn_blocking(move || db.create_session()?.import_change(&change, &parent_node_id))
                .await?;
        match import_result {
            Err(anyhow_err) => {
                if let Some(DatabaseError::UnexpectedChangeNumber {
                    expected, actual, ..
                }) = anyhow_err.downcast_ref::<DatabaseError>()
                {
                    info!(
                        "Unexpected change number {} (expected {}) from {}",
                        actual,
                        expected,
                        self.server.url_prefix()
                    );
                    debug!("Importing the changes from {}", self.server.url_prefix());
                    let (first, last) = self
                        .server
                        .import_changes(self.db.clone(), self.pubsub.clone(), self.parent_node_id)
                        .await?;
                    info!(
                        "Imported changes {}-{} from {}",
                        first,
                        last,
                        self.server.url_prefix()
                    );
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
