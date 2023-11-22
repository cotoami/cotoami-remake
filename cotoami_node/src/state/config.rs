use std::path::PathBuf;

use cotoami_db::prelude::*;
use dotenvy::dotenv;
use validator::Validate;

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

    pub fn owner_password(&self) -> &str { self.owner_password.as_deref().unwrap() }

    pub fn session_seconds(&self) -> u64 { self.session_minutes * 60 }
}
