use std::path::PathBuf;

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use dotenvy::dotenv;
use validator::Validate;

/////////////////////////////////////////////////////////////////////////////
// NodeConfig
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize, Validate)]
pub struct NodeConfig {
    /// `COTOAMI_DB_DIR`
    pub db_dir: Option<String>,

    /// `COTOAMI_NODE_NAME`
    ///
    /// It will be used to define a node name when the node is first initialized.
    #[validate(length(min = 1, max = "Node::NAME_MAX_LENGTH"))]
    pub node_name: Option<String>,

    /// `COTOAMI_OWNER_PASSWORD`
    ///
    /// An owner password is required if you want the local node to be launched as a server or
    /// to connect to other server nodes.
    ///
    /// This value will be used:
    ///
    /// 1. to configure the local node password ([LocalNode::owner_password_hash])
    ///    when it has not yet configured.
    /// 2. to authenticate an owner who is about to create a [crate::state::NodeState].
    /// 3. to authenticate remote clients as owners.
    /// 4. as a master password to encrypt other passwords.
    pub owner_password: Option<String>,

    /// `COTOAMI_OWNER_REMOTE_NODE_ID`
    ///
    /// If this value and [Self::owner_remote_node_password] is configured, it registers
    /// the node of the given ID as a client/child that has an owner privilege to this node
    /// during launching a node server.
    ///
    /// Because this value and [Self::owner_remote_node_password] is used only to register
    /// the given node as a new client/child, if there is already an exising child node with
    /// the same ID, these values have no effect.
    pub owner_remote_node_id: Option<Id<Node>>,

    /// `COTOAMI_OWNER_REMOTE_NODE_PASSWORD`
    pub owner_remote_node_password: Option<String>,

    /// `COTOAMI_SESSION_MINUTES`
    #[serde(default = "NodeConfig::default_session_minutes")]
    pub session_minutes: u64,

    /// `COTOAMI_CHANGES_CHUNK_SIZE`
    ///
    /// The number of changes to be fetched at once when the node is asked to.
    /// The larger this value is, the less websocket messages will be sent when synchronizing
    /// changes, but the more memory will be used to store the changes and you have to configure
    /// the `max_message_size_as_client` and `max_message_size_as_server` accordingly.
    #[serde(default = "NodeConfig::default_changes_chunk_size")]
    pub changes_chunk_size: i64,

    /// `COTOAMI_MAX_MESSAGE_SIZE_AS_CLIENT`
    ///
    /// The maximum size of an incoming message when the node acts as a client.
    /// cf. https://docs.rs/tungstenite/latest/tungstenite/protocol/struct.WebSocketConfig.html#structfield.max_message_size
    #[serde(default = "NodeConfig::default_max_message_size_as_client")]
    pub max_message_size_as_client: Option<usize>,

    /// `COTOAMI_MAX_MESSAGE_SIZE_AS_SERVER`
    ///
    /// The maximum size of an incoming message when the node acts as a server.
    /// cf. https://docs.rs/tungstenite/latest/tungstenite/protocol/struct.WebSocketConfig.html#structfield.max_message_size
    #[serde(default = "NodeConfig::default_max_message_size_as_server")]
    pub max_message_size_as_server: Option<usize>,

    /// `COTOAMI_PLUGINS_DIR`
    pub plugins_dir: Option<String>,
}

impl NodeConfig {
    const ENV_PREFIX: &'static str = "COTOAMI_";
    const DEFAULT_DB_DIR_NAME: &'static str = "cotoami";
    const DEFAULT_PLUGINS_DIR_NAME: &'static str = "plugins";

    pub fn load_from_env() -> Result<Self, envy::Error> {
        dotenv().ok();
        envy::prefixed(Self::ENV_PREFIX).from_env::<Self>()
    }

    pub fn new_standalone(db_dir: Option<String>, node_name: Option<String>) -> Self {
        Self {
            db_dir,
            node_name,
            owner_password: None,
            owner_remote_node_id: None,
            owner_remote_node_password: None,
            session_minutes: Self::default_session_minutes(),
            changes_chunk_size: Self::default_changes_chunk_size(),
            max_message_size_as_client: Self::default_max_message_size_as_client(),
            max_message_size_as_server: Self::default_max_message_size_as_server(),
            plugins_dir: None,
        }
    }

    // Functions returning a default value as a workaround for the issue:
    // https://github.com/serde-rs/serde/issues/368
    fn default_session_minutes() -> u64 { 60 * 24 }
    fn default_changes_chunk_size() -> i64 { 30 }
    fn default_max_message_size_as_client() -> Option<usize> {
        Some(1 << 30) // 1 GiB
    }
    fn default_max_message_size_as_server() -> Option<usize> {
        Some(64 << 20) // 64 MiB
    }

    pub fn db_dir(&self) -> PathBuf {
        self.db_dir.as_ref().map(PathBuf::from).unwrap_or_else(|| {
            dirs::home_dir()
                .map(|mut path| {
                    path.push(Self::DEFAULT_DB_DIR_NAME);
                    path
                })
                .unwrap_or(PathBuf::from(Self::DEFAULT_DB_DIR_NAME))
        })
    }

    pub fn plugins_dir(&self) -> PathBuf {
        self.plugins_dir
            .as_ref()
            .map(PathBuf::from)
            .unwrap_or_else(|| {
                let mut path = self.db_dir();
                path.push(Self::DEFAULT_PLUGINS_DIR_NAME);
                path
            })
    }

    pub fn try_get_owner_password(&self) -> Result<&str> {
        self.owner_password.as_deref().ok_or(anyhow!(
            "Owner password is required to invoke this operation."
        ))
    }

    pub fn session_seconds(&self) -> u64 { self.session_minutes * 60 }
}

/////////////////////////////////////////////////////////////////////////////
// ServerConfig
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize, Validate)]
pub struct ServerConfig {
    // COTOAMI_SERVER_PORT
    #[serde(default = "ServerConfig::default_port")]
    pub port: u16,

    // COTOAMI_SERVER_URL_SCHEME
    #[serde(default = "ServerConfig::default_url_scheme")]
    pub url_scheme: String,

    // COTOAMI_SERVER_URL_HOST
    #[serde(default = "ServerConfig::default_url_host")]
    pub url_host: String,

    // COTOAMI_SERVER_URL_PORT
    pub url_port: Option<u16>,

    // COTOAMI_SERVER_ENABLE_WEBSOCKET
    #[serde(default = "ServerConfig::default_enable_websocket")]
    pub enable_websocket: bool,
}

impl ServerConfig {
    const ENV_PREFIX: &'static str = "COTOAMI_SERVER_";

    pub fn load_from_env() -> Result<Self, envy::Error> {
        dotenv().ok();
        envy::prefixed(Self::ENV_PREFIX).from_env::<Self>()
    }

    // Functions returning a default value as a workaround for the issue:
    // https://github.com/serde-rs/serde/issues/368
    fn default_port() -> u16 { 5103 }
    fn default_url_scheme() -> String { "http".into() }
    fn default_url_host() -> String { "localhost".into() }
    fn default_enable_websocket() -> bool { true }
}

impl Default for ServerConfig {
    fn default() -> Self {
        Self {
            port: Self::default_port(),
            url_scheme: Self::default_url_scheme(),
            url_host: Self::default_url_host(),
            url_port: Some(Self::default_port()),
            enable_websocket: Self::default_enable_websocket(),
        }
    }
}
