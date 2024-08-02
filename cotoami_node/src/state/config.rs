use std::path::PathBuf;

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use dotenvy::dotenv;
use validator::Validate;

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, Validate)]
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

    /// `COTOAMI_SESSION_MINUTES`
    #[serde(default = "NodeConfig::default_session_minutes")]
    pub session_minutes: u64,

    /// `COTOAMI_CHANGES_CHUNK_SIZE`
    #[serde(default = "NodeConfig::default_changes_chunk_size")]
    pub changes_chunk_size: i64,

    /// `COTOAMI_IMAGE_MAX_SIZE`
    ///
    /// [None] means no resizing will be applied to incoming images.
    ///
    /// Since the value will be deserialized to the default value even if
    /// you omit the value in a config file or env values, there's no way to configure
    /// this value to [None] for now.
    #[serde(default = "NodeConfig::default_image_max_size")]
    pub image_max_size: Option<i32>,
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
            session_minutes: Self::default_session_minutes(),
            changes_chunk_size: Self::default_changes_chunk_size(),
            image_max_size: Self::default_image_max_size(),
        }
    }

    // Functions returning a default value as a workaround for the issue:
    // https://github.com/serde-rs/serde/issues/368
    fn default_session_minutes() -> u64 { 60 }
    fn default_changes_chunk_size() -> i64 { 100 }
    fn default_image_max_size() -> Option<i32> { Some(1200) }

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
