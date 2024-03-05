use std::path::PathBuf;

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use dotenvy::dotenv;
use validator::Validate;

#[derive(Debug, serde::Deserialize, Validate)]
pub struct NodeConfig {
    // COTOAMI_DB_DIR
    pub db_dir: Option<String>,

    // COTOAMI_NODE_NAME
    #[validate(length(min = 1, max = "Node::NAME_MAX_LENGTH"))]
    pub node_name: Option<String>,

    /// The owner password is used for owner authentication and
    /// as a master password to encrypt other passwords. It is required if
    /// you want this node to be launched as a server or to connect to other nodes.
    ///
    /// * This value can be set via the environment variable:
    /// `COTOAMI_OWNER_PASSWORD`.
    pub owner_password: Option<String>,

    /// The owner password will be changed to the value of [Config::owner_password] if:
    /// 1. This value is true.
    /// 2. [Config::owner_password] has `Some` value.
    /// 3. The local node has already been initialized (meaning there's an existing password).
    ///
    /// * This value can be set via the environment variable:
    /// `COTOAMI_CHANGE_OWNER_PASSWORD`.
    #[serde(default = "NodeConfig::default_change_owner_password")]
    pub change_owner_password: bool,

    // COTOAMI_SESSION_MINUTES
    #[serde(default = "NodeConfig::default_session_minutes")]
    pub session_minutes: u64,

    // COTOAMI_CHANGES_CHUNK_SIZE
    #[serde(default = "NodeConfig::default_changes_chunk_size")]
    pub changes_chunk_size: i64,
}

impl NodeConfig {
    const ENV_PREFXI: &'static str = "COTOAMI_";
    const DEFAULT_DB_DIR_NAME: &'static str = "cotoami";

    pub fn load_from_env() -> Result<Self, envy::Error> {
        dotenv().ok();
        envy::prefixed(Self::ENV_PREFXI).from_env::<Self>()
    }

    pub fn new_standalone(db_dir: Option<String>, node_name: Option<String>) -> Self {
        Self {
            db_dir,
            node_name,
            owner_password: None,
            change_owner_password: false,
            session_minutes: Self::default_session_minutes(),
            changes_chunk_size: Self::default_changes_chunk_size(),
        }
    }

    // Functions returning a default value as a workaround for the issue:
    // https://github.com/serde-rs/serde/issues/368
    fn default_change_owner_password() -> bool { false }
    fn default_session_minutes() -> u64 { 60 }
    fn default_changes_chunk_size() -> i64 { 1000 }

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

    pub fn try_get_owner_password(&self) -> Result<&str> {
        self.owner_password.as_deref().ok_or(anyhow!(
            "The owner password is required to invoke this operation."
        ))
    }

    pub fn session_seconds(&self) -> u64 { self.session_minutes * 60 }
}
