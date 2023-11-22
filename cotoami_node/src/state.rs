use std::{convert::Infallible, fs, path::PathBuf, sync::Arc};

use anyhow::{anyhow, Result};
use axum::response::sse::Event;
use cotoami_db::prelude::*;
use dotenvy::dotenv;
use parking_lot::{MappedRwLockReadGuard, RwLock, RwLockReadGuard};
use tokio::task::spawn_blocking;
use validator::Validate;

use self::conn::{ServerConnection, ServerConnections};
use crate::pubsub::Publisher;

mod changes;
pub(crate) mod conn;
mod error;
mod nodes;
mod parents;
mod service;
mod session;

/////////////////////////////////////////////////////////////////////////////
// AppState
/////////////////////////////////////////////////////////////////////////////

#[derive(Clone)]
pub struct AppState {
    config: Arc<Config>,
    db: Arc<Database>,
    pubsub: Pubsub,
    server_conns: Arc<RwLock<ServerConnections>>,
}

impl AppState {
    pub fn new(config: Config) -> Result<Self> {
        config.validate()?;

        let db_dir = config.db_dir();
        fs::create_dir(&db_dir).ok();
        let db = Database::new(db_dir)?;

        let pubsub = Pubsub::new();

        Ok(AppState {
            config: Arc::new(config),
            db: Arc::new(db),
            pubsub,
            server_conns: Arc::new(RwLock::new(ServerConnections::default())),
        })
    }

    pub fn config(&self) -> &Arc<Config> { &self.config }

    pub fn db(&self) -> &Arc<Database> { &self.db }

    pub fn pubsub(&self) -> &Pubsub { &self.pubsub }

    pub fn server_conn(
        &self,
        server_id: &Id<Node>,
    ) -> Result<MappedRwLockReadGuard<ServerConnection>> {
        RwLockReadGuard::try_map(self.server_conns.read(), |conns| conns.get(server_id))
            .map_err(|_| anyhow!("ServerConnection for {} not found", server_id))
    }

    pub fn contains_server(&self, server_id: &Id<Node>) -> bool {
        self.server_conns.read().contains_key(server_id)
    }

    pub fn put_server_conn(&self, server_id: &Id<Node>, server_conn: ServerConnection) {
        self.server_conns.write().insert(*server_id, server_conn);
    }

    pub fn read_server_conns(&self) -> RwLockReadGuard<ServerConnections> {
        self.server_conns.read()
    }

    pub async fn restore_server_conns(&self) -> Result<()> {
        let db = self.db.clone();
        let (local_node, server_nodes) = spawn_blocking(move || {
            let mut db = db.new_session()?;
            let operator = db.local_node_as_operator()?;
            Ok::<_, anyhow::Error>((
                db.local_node_pair(&operator)?.1,
                db.all_server_nodes(&operator)?,
            ))
        })
        .await??;

        let mut server_conns = self.server_conns.write();
        server_conns.clear();
        for (server_node, _) in server_nodes.iter() {
            let server_conn = if server_node.disabled {
                ServerConnection::Disabled
            } else {
                ServerConnection::connect(server_node, local_node.clone(), self).await
            };
            server_conns.insert(server_node.node_id, server_conn);
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

    pub fn session_seconds(&self) -> u64 { self.session_minutes * 60 }
}

/////////////////////////////////////////////////////////////////////////////
// Pubsub
/////////////////////////////////////////////////////////////////////////////

#[derive(Clone)]
pub struct Pubsub {
    pub local_change: Arc<ChangePubsub>,
    pub sse_change: Arc<SsePubsub>,
}

impl Pubsub {
    fn new() -> Self {
        let local_change = ChangePubsub::new();
        let sse_change = SsePubsub::new();

        sse_change.tap_into(
            local_change.subscribe(None::<()>),
            |_| None,
            |change| {
                let event = Event::default().event("change").json_data(change)?;
                Ok(Ok(event))
            },
        );

        Self {
            local_change: Arc::new(local_change),
            sse_change: Arc::new(sse_change),
        }
    }

    pub fn publish_change(&self, changelog: ChangelogEntry) {
        self.local_change.publish(changelog, None);
    }
}

pub(crate) type ChangePubsub = Publisher<ChangelogEntry, ()>;
pub(crate) type SsePubsub = Publisher<Result<Event, Infallible>, ()>;
