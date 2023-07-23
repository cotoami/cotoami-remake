use anyhow::Result;
use axum::http::Uri;
use axum::response::sse::Event;
use axum::response::IntoResponse;
use axum::{Router, Server};
use cotoami_db::prelude::*;
use dotenvy::dotenv;
use parking_lot::Mutex;
use pubsub::Publisher;
use std::convert::Infallible;
use std::fs;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;
use tracing::info;

mod api;
mod pubsub;

#[tokio::main]
async fn main() -> Result<()> {
    // Install global collector configured based on RUST_LOG env var.
    tracing_subscriber::fmt::init();

    let config = Config::load()?;
    info!("Config loaded: {:?}", config);

    let addr = SocketAddr::from(([0, 0, 0, 0], config.port));

    let pubsub = Publisher::<Result<Event, Infallible>>::new();

    let db_dir = config.db_dir();
    fs::create_dir(&db_dir).ok();
    let db = Database::new(db_dir)?;

    let state = AppState {
        config: Arc::new(config),
        pubsub: Arc::new(Mutex::new(pubsub)),
        db: Arc::new(db),
    };

    let router = Router::new()
        .nest("/api", api::routes())
        .fallback(fallback)
        .with_state(state);

    Server::bind(&addr)
        .serve(router.into_make_service())
        .await
        .unwrap();

    Ok(())
}

/// axum handler for any request that fails to match the router routes.
/// This implementation returns HTTP status code Not Found (404).
async fn fallback(uri: Uri) -> impl IntoResponse {
    (
        axum::http::StatusCode::NOT_FOUND,
        format!("No route {}", uri),
    )
}

/////////////////////////////////////////////////////////////////////////////
// Config
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, serde::Deserialize)]
struct Config {
    #[serde(default = "Config::default_port")]
    port: u16,
    db_dir: Option<String>,
    owner_password: Option<String>,
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
    fn default_port() -> u16 {
        5103
    }

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
