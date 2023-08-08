use std::{convert::Infallible, fs, net::SocketAddr, path::PathBuf, sync::Arc};

use anyhow::Result;
use axum::{
    http::{StatusCode, Uri},
    middleware,
    response::{sse::Event, IntoResponse},
    Extension, Router, Server,
};
use cotoami_db::prelude::*;
use dotenvy::dotenv;
use parking_lot::Mutex;
use pubsub::Publisher;
use tracing::info;

mod api;
mod csrf;
mod error;
mod pubsub;

#[tokio::main]
async fn main() -> Result<()> {
    // Install global collector configured based on RUST_LOG env var.
    tracing_subscriber::fmt::init();

    let config = Config::load()?;
    let port = config.port;
    info!("Config loaded: {:?}", config);

    let pubsub = Publisher::<Result<Event, Infallible>>::new();

    let db_dir = config.db_dir();
    fs::create_dir(&db_dir).ok();
    let db = Database::new(db_dir)?;

    // TODO: initialize node

    let state = AppState {
        config: Arc::new(config),
        pubsub: Arc::new(Mutex::new(pubsub)),
        db: Arc::new(db),
    };

    let router = Router::new()
        .nest("/api", api::routes())
        .fallback(fallback)
        .layer(middleware::from_fn(csrf::protect_from_forgery))
        .layer(Extension(state.clone())) // for middleware
        .with_state(state);

    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    Server::bind(&addr)
        .serve(router.into_make_service())
        .await
        .unwrap();

    Ok(())
}

/// axum handler for any request that fails to match the router routes.
/// This implementation returns HTTP status code Not Found (404).
async fn fallback(uri: Uri) -> impl IntoResponse {
    (StatusCode::NOT_FOUND, format!("No route: {}", uri.path()))
}

/////////////////////////////////////////////////////////////////////////////
// Config
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, serde::Deserialize)]
struct Config {
    // COTOAMI_PORT
    #[serde(default = "Config::default_port")]
    port: u16,

    // COTOAMI_URL_SCHEME
    #[serde(default = "Config::default_url_scheme")]
    url_scheme: String,
    // COTOAMI_URL_HOST
    #[serde(default = "Config::default_url_host")]
    url_host: String,
    // COTOAMI_URL_PORT
    url_port: Option<u16>,

    // COTOAMI_DB_DIR
    db_dir: Option<String>,
    // COTOAMI_DB_NAME
    db_name: Option<String>,

    // COTOAMI_OWNER_PASSWORD
    owner_password: Option<String>,

    // COTOAMI_SESSION_MINUTES
    #[serde(default = "Config::default_session_minutes")]
    session_minutes: u64,
}

impl Config {
    const ENV_PREFXI: &str = "COTOAMI_";
    const DEFAULT_DB_DIR_NAME: &str = "cotoami";

    fn load() -> Result<Config, envy::Error> {
        dotenv().ok();
        envy::prefixed(Self::ENV_PREFXI).from_env::<Config>()
    }

    // Functions returning a default value as a workaround for the issue:
    // https://github.com/serde-rs/serde/issues/368
    fn default_port() -> u16 { 5103 }
    fn default_url_scheme() -> String { "http".into() }
    fn default_url_host() -> String { "localhost".into() }
    fn default_session_minutes() -> u64 { 60 }

    fn db_dir(&self) -> PathBuf {
        self.db_dir
            .as_ref()
            .map(|path| PathBuf::from(path))
            .unwrap_or_else(|| {
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
// AppState
/////////////////////////////////////////////////////////////////////////////

#[derive(Clone)]
struct AppState {
    config: Arc<Config>,
    pubsub: Arc<Mutex<Publisher<Result<Event, Infallible>>>>,
    db: Arc<Database>,
}

impl AppState {
    fn publish_change(&self, changelog: ChangelogEntry) -> Result<()> {
        let event = Event::default().event("change").json_data(changelog)?;
        self.pubsub.lock().publish(&Ok(event));
        Ok(())
    }
}
