use std::{
    collections::HashMap, convert::Infallible, fs, net::SocketAddr, path::PathBuf, sync::Arc,
};

use anyhow::{anyhow, Context, Result};
use axum::{
    http::{StatusCode, Uri},
    middleware,
    response::{sse::Event, IntoResponse},
    Extension, Router,
};
use cotoami_db::prelude::*;
use dotenvy::dotenv;
use parking_lot::{MappedRwLockReadGuard, Mutex, RwLock, RwLockReadGuard};
use pubsub::Publisher;
use tokio::{
    sync::{oneshot, oneshot::Sender},
    task::{spawn_blocking, JoinHandle},
};
use tracing::{debug, info};
use validator::Validate;

use crate::{
    client::{EventLoop, EventLoopState, Server},
    http::session::Session,
};

mod api;
mod client;
mod csrf;
mod http;
mod pubsub;
mod service;

pub async fn launch_server(config: Config) -> Result<(JoinHandle<Result<()>>, Sender<()>)> {
    let state = AppState::new(config)?;
    let port = state.config.port;

    state.init_local_node().await?;
    state.restore_parent_conns().await?;

    let router = Router::new()
        .nest("/api", http::routes())
        .fallback(fallback)
        .layer(middleware::from_fn(csrf::protect_from_forgery))
        .layer(Extension(state.clone())) // for middleware
        .with_state(state);

    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    let server = axum::Server::bind(&addr).serve(router.into_make_service());

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
// AppState
/////////////////////////////////////////////////////////////////////////////

#[derive(Clone)]
struct AppState {
    config: Arc<Config>,
    db: Arc<Database>,
    pubsub: Arc<Pubsub>,
    parent_conns: Arc<RwLock<ParentConns>>,
}

impl AppState {
    fn new(config: Config) -> Result<Self> {
        config.validate()?;

        let db_dir = config.db_dir();
        fs::create_dir(&db_dir).ok();
        let db = Database::new(db_dir)?;

        Ok(AppState {
            config: Arc::new(config),
            db: Arc::new(db),
            pubsub: Arc::new(Pubsub::new()),
            parent_conns: Arc::new(RwLock::new(HashMap::default())),
        })
    }

    async fn init_local_node(&self) -> Result<()> {
        let (config, db) = (self.config.clone(), self.db.clone());
        spawn_blocking(move || {
            let mut db = db.new_session()?;
            let owner_password = config.owner_password();

            // If the local node already exists,
            // its name and password can be changed via config
            if db.has_local_node_initialized() {
                let opr = db.local_node_as_operator()?;
                let (ext, node) = db.local_node_pair(&opr)?;
                if let Some(name) = config.node_name.as_deref() {
                    if name != node.name {
                        // Ignoring the changelog since this function is called during
                        // the server startup (there should be no child nodes connected).
                        let (node, _) = db.rename_local_node(name, &opr)?;
                        info!("The node name has been changed to [{}].", node.name);
                    }
                }

                if config.change_owner_password {
                    db.change_owner_password(owner_password)?;
                    info!("The owner password has been changed.");
                } else {
                    ext.verify_password(owner_password)
                        .context("Config::owner_password couldn't be verified.")?;
                }
                return Ok(());
            }

            // Initialize the local node
            let name = config.node_name.as_deref();
            let ((_, node), _) = db.init_as_node(name, Some(owner_password))?;
            info!(
                "The local node [{}]({}) has been created",
                node.name, node.uuid
            );
            Ok(())
        })
        .await?
    }

    fn parent_conn(&self, parent_id: &Id<Node>) -> Result<MappedRwLockReadGuard<ParentConn>> {
        RwLockReadGuard::try_map(self.parent_conns.read(), |conns| conns.get(parent_id))
            .map_err(|_| anyhow!("ParentConn for {} not found", parent_id))
    }

    fn put_parent_conn(&self, parent_id: &Id<Node>, session: Session, event_loop: EventLoop) {
        let parent_conn = ParentConn::new(session, event_loop);
        self.parent_conns.write().insert(*parent_id, parent_conn);
    }

    async fn restore_parent_conns(&self) -> Result<()> {
        let db = self.db.clone();
        let (local_node, parent_nodes) = spawn_blocking(move || {
            let mut db = db.new_session()?;
            let operator = db.local_node_as_operator()?;
            Ok::<_, anyhow::Error>((
                db.local_node_pair(&operator)?.1,
                db.all_parent_nodes(&operator)?,
            ))
        })
        .await??;

        let mut parent_conns = self.parent_conns.write();
        parent_conns.clear();
        for (parent_node, _) in parent_nodes.iter() {
            let parent_conn = if parent_node.disabled {
                ParentConn::Disabled
            } else {
                ParentConn::connect(
                    parent_node,
                    &local_node,
                    &self.config,
                    &self.db,
                    &self.pubsub,
                )
                .await
            };
            parent_conns.insert(parent_node.node_id, parent_conn);
        }
        Ok(())
    }
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

    /// The owner password is used for owner authentication and
    /// as a master password to encrypt other passwords.
    ///
    /// * This value is required to launch a node server.
    /// * This value can be set via the environment variable:
    /// `COTOAMI_OWNER_PASSWORD`.
    #[validate(required)]
    pub owner_password: Option<String>,

    /// The owner password will be changed to the value of [Config::owner_password] if:
    /// 1. This value is true.
    /// 2. The local node has already been initialized (meaning there's an existing password).
    ///
    /// * This value can be set via the environment variable:
    /// `COTOAMI_CHANGE_OWNER_PASSWORD`.
    #[serde(default = "Config::default_change_owner_password")]
    pub change_owner_password: bool,

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
    fn default_change_owner_password() -> bool { false }
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

    pub fn owner_password(&self) -> &str { self.owner_password.as_deref().unwrap() }

    fn session_seconds(&self) -> u64 { self.session_minutes * 60 }
}

/////////////////////////////////////////////////////////////////////////////
// Pubsub
/////////////////////////////////////////////////////////////////////////////

struct Pubsub {
    pub local_change: Mutex<LocalChangePubsub>,
    pub sse: Mutex<SsePubsub>,
}

impl Pubsub {
    fn new() -> Self {
        let mut local_change = LocalChangePubsub::new();
        let sse = SsePubsub::new();

        sse.tap_into(
            local_change.subscribe(None::<()>),
            Some(SsePubsubTopic::Change),
            |change| {
                let event = Event::default().event("change").json_data(change)?;
                Ok(Ok(event))
            },
        );

        Self {
            local_change: Mutex::new(local_change),
            sse: Mutex::new(sse),
        }
    }

    fn publish_change(&self, changelog: &ChangelogEntry) {
        self.local_change.lock().publish(changelog, None);
    }
}

type LocalChangePubsub = Publisher<ChangelogEntry, ()>;
type SsePubsub = Publisher<Result<Event, Infallible>, SsePubsubTopic>;

#[derive(Clone, PartialEq, Eq, Hash)]
enum SsePubsubTopic {
    Change,
}

/////////////////////////////////////////////////////////////////////////////
// ParentConn
/////////////////////////////////////////////////////////////////////////////

enum ParentConn {
    Disabled,
    InitFailed(anyhow::Error),
    Connected {
        session: Session,
        event_loop_state: Arc<RwLock<EventLoopState>>,
    },
}

impl ParentConn {
    fn new(session: Session, mut event_loop: EventLoop) -> Self {
        let parent_conn = ParentConn::Connected {
            session,
            event_loop_state: event_loop.state(),
        };
        tokio::spawn(async move {
            event_loop.start().await;
        });
        parent_conn
    }

    async fn connect(
        parent_node: &ParentNode,
        local_node: &Node,
        config: &Config,
        db: &Arc<Database>,
        pubsub: &Arc<Pubsub>,
    ) -> Self {
        match Self::try_connect(parent_node, local_node, config, db, pubsub).await {
            Ok(conn) => conn,
            Err(err) => {
                debug!("Failed to initialize a parent connection: {:?}", err);
                ParentConn::InitFailed(err)
            }
        }
    }

    async fn try_connect(
        parent_node: &ParentNode,
        local_node: &Node,
        config: &Config,
        db: &Arc<Database>,
        pubsub: &Arc<Pubsub>,
    ) -> Result<Self> {
        let mut server = Server::new(parent_node.url_prefix.clone())?;

        // Attempt to log in to the parent node
        let password = parent_node
            .password(config.owner_password())?
            .ok_or(anyhow!("Parent password is missing."))?;
        let child_session = server
            .create_child_session(password, None, &local_node)
            .await?;
        info!("Successfully logged in to {}", server.url_prefix());

        // Import the changelog
        server
            .import_changes(db, pubsub, parent_node.node_id)
            .await?;

        // Create an event stream
        let event_loop = server
            .create_event_loop(parent_node.node_id, db, pubsub)
            .await?;

        Ok(Self::new(child_session.session, event_loop))
    }

    fn disable_event_loop(&self) {
        if let ParentConn::Connected {
            event_loop_state, ..
        } = self
        {
            event_loop_state.write().disable();
        }
    }

    fn restart_event_loop_if_possible(&self) -> bool {
        if let ParentConn::Connected {
            event_loop_state, ..
        } = self
        {
            event_loop_state.write().restart_if_possible()
        } else {
            false
        }
    }
}

type ParentConns = HashMap<Id<Node>, ParentConn>;
