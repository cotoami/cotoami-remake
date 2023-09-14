use std::{
    collections::HashMap, convert::Infallible, fs, net::SocketAddr, path::PathBuf, sync::Arc,
};

use anyhow::{bail, Result};
use axum::{
    http::{StatusCode, Uri},
    middleware,
    response::{sse::Event, IntoResponse},
    Extension, Router, Server,
};
use cotoami_db::prelude::*;
use dotenvy::dotenv;
use parking_lot::{Mutex, RwLock};
use pubsub::Publisher;
use tokio::{
    sync::{oneshot, oneshot::Sender},
    task::JoinHandle,
};
use tracing::info;
use validator::Validate;

use crate::{
    api::session::Session,
    client::{EventLoop, EventLoopState},
};

mod api;
mod client;
mod csrf;
mod error;
mod pubsub;

pub async fn run_server(config: Config) -> Result<(JoinHandle<Result<()>>, Sender<()>)> {
    let state = AppState::new(config)?;
    let port = state.config.port;

    // Should this be in `spawn_blocking`?
    state.init_local_node()?;

    let router = Router::new()
        .nest("/api", api::routes())
        .fallback(fallback)
        .layer(middleware::from_fn(csrf::protect_from_forgery))
        .layer(Extension(state.clone())) // for middleware
        .with_state(state);

    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    let server = Server::bind(&addr).serve(router.into_make_service());

    let (tx, rx) = oneshot::channel::<()>();
    let server = server.with_graceful_shutdown(async {
        rx.await.ok();
    });

    let handle = tokio::spawn(async move {
        server.await?;
        Ok(())
    });

    Ok((handle, tx))
}

/// axum handler for any request that fails to match the router routes.
/// This implementation returns HTTP status code Not Found (404).
async fn fallback(uri: Uri) -> impl IntoResponse {
    (StatusCode::NOT_FOUND, format!("No route: {}", uri.path()))
}

/////////////////////////////////////////////////////////////////////////////
// Config
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, serde::Deserialize, Validate)]
pub struct Config {
    // COTOAMI_PORT
    #[serde(default = "Config::default_port")]
    pub port: u16,

    // COTOAMI_URL_SCHEME
    #[serde(default = "Config::default_url_scheme")]
    pub url_scheme: String,
    // COTOAMI_URL_HOST
    #[serde(default = "Config::default_url_host")]
    pub url_host: String,
    // COTOAMI_URL_PORT
    pub url_port: Option<u16>,

    // COTOAMI_DB_DIR
    pub db_dir: Option<String>,

    // COTOAMI_NODE_NAME
    #[validate(length(min = 1, max = "Node::NAME_MAX_LENGTH"))]
    pub node_name: Option<String>,

    // COTOAMI_OWNER_PASSWORD
    pub owner_password: Option<String>,

    // COTOAMI_SESSION_MINUTES
    #[serde(default = "Config::default_session_minutes")]
    pub session_minutes: u64,

    // COTOAMI_CHANGES_CHUNK_SIZE
    #[serde(default = "Config::default_changes_chunk_size")]
    pub changes_chunk_size: i64,
}

impl Config {
    const ENV_PREFXI: &str = "COTOAMI_";
    const DEFAULT_DB_DIR_NAME: &str = "cotoami";

    pub fn load_from_env() -> Result<Config, envy::Error> {
        dotenv().ok();
        envy::prefixed(Self::ENV_PREFXI).from_env::<Config>()
    }

    // Functions returning a default value as a workaround for the issue:
    // https://github.com/serde-rs/serde/issues/368
    fn default_port() -> u16 { 5103 }
    fn default_url_scheme() -> String { "http".into() }
    fn default_url_host() -> String { "localhost".into() }
    fn default_session_minutes() -> u64 { 60 }
    fn default_changes_chunk_size() -> i64 { 1000 }

    fn db_dir(&self) -> PathBuf {
        self.db_dir.as_ref().map(PathBuf::from).unwrap_or_else(|| {
            dirs::home_dir()
                .map(|mut path| {
                    path.push(Self::DEFAULT_DB_DIR_NAME);
                    path
                })
                .unwrap_or(PathBuf::from(Self::DEFAULT_DB_DIR_NAME))
        })
    }

    fn session_seconds(&self) -> u64 { self.session_minutes * 60 }
}

/////////////////////////////////////////////////////////////////////////////
// Pubsub
/////////////////////////////////////////////////////////////////////////////

type Pubsub = Publisher<Result<Event, Infallible>>;

trait ChangePub {
    fn publish_change(&mut self, changelog: ChangelogEntry) -> Result<()>;
}

impl ChangePub for Pubsub {
    fn publish_change(&mut self, changelog: ChangelogEntry) -> Result<()> {
        let event = Event::default().event("change").json_data(changelog)?;
        self.publish(&Ok(event));
        Ok(())
    }
}

/////////////////////////////////////////////////////////////////////////////
// ParentConn
/////////////////////////////////////////////////////////////////////////////

enum ParentConn {
    Failed(anyhow::Error),
    Connected {
        session: Session,
        event_loop_state: Arc<RwLock<EventLoopState>>,
    },
}

impl ParentConn {
    pub fn end_event_loop(&self) {
        if let ParentConn::Connected {
            event_loop_state, ..
        } = self
        {
            event_loop_state.write().end();
        }
    }
}

type ParentConns = HashMap<Id<Node>, ParentConn>;

/////////////////////////////////////////////////////////////////////////////
// AppState
/////////////////////////////////////////////////////////////////////////////

#[derive(Clone)]
struct AppState {
    config: Arc<Config>,
    db: Arc<Database>,
    pubsub: Arc<Mutex<Pubsub>>,
    parent_conns: Arc<Mutex<ParentConns>>,
}

impl AppState {
    fn new(config: Config) -> Result<Self> {
        config.validate()?;

        let db_dir = config.db_dir();
        fs::create_dir(&db_dir).ok();
        let db = Database::new(db_dir)?;

        let pubsub = Pubsub::new();

        let parent_conns = HashMap::default();
        // TODO restore sessions

        Ok(AppState {
            config: Arc::new(config),
            db: Arc::new(db),
            pubsub: Arc::new(Mutex::new(pubsub)),
            parent_conns: Arc::new(Mutex::new(parent_conns)),
        })
    }

    fn init_local_node(&self) -> Result<()> {
        let db = self.db.create_session()?;

        // If the local node already exists,
        // its name and password can be changed via config
        if db.is_local_node_initialized() {
            if let Some(name) = self.config.node_name.as_deref() {
                db.rename_local_node(name)?;
                info!("The node name has been changed via COTOAMI_NODE_NAME.");
            }
            if let Some(password) = self.config.owner_password.as_deref() {
                db.change_owner_password(password)?;
                info!("The owner password has been changed via COTOAMI_OWNER_PASSWORD.");
            }
            return Ok(());
        }

        // Create a local node
        if let Some(password) = self.config.owner_password.as_deref() {
            let name = self.config.node_name.as_deref();
            let ((_, node), _) = db.init_as_node(name, Some(password))?;
            info!("The local node [{}] has been created", node.name);
        } else {
            bail!("COTOAMI_OWNER_PASSWORD must be set for the first startup.");
        }
        Ok(())
    }

    pub fn put_parent_conn(
        &self,
        parent_id: &Id<Node>,
        session: Session,
        mut event_loop: EventLoop,
    ) {
        let parent_conn = ParentConn::Connected {
            session,
            event_loop_state: event_loop.state(),
        };
        self.parent_conns.lock().insert(*parent_id, parent_conn);
        tokio::spawn(async move {
            event_loop.start().await;
        });
    }
}
