use anyhow::Result;
use axum::{
    http::StatusCode,
    http::Uri,
    response::sse::Event,
    response::{IntoResponse, Response},
    routing::get,
    Router, Server,
};
use cotoami_db::prelude::*;
use dotenvy::dotenv;
use pubsub::Publisher;
use std::convert::Infallible;
use std::fs;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use tracing::{error, info};

pub mod pubsub;

#[tokio::main]
async fn main() -> Result<()> {
    // Install global collector configured based on RUST_LOG env var.
    tracing_subscriber::fmt::init();

    let config = Config::load()?;
    info!("Config loaded: {:?}", config);
    let port = config.port; // save it before moving to the state

    let pubsub = Publisher::<Result<Event, Infallible>>::new();

    let db_dir = config.db_dir();
    fs::create_dir(&db_dir).ok();
    let db = Database::new(db_dir)?;

    let state = AppState {
        config: Arc::new(config),
        pubsub: Arc::new(Mutex::new(pubsub)),
        db: Arc::new(db),
    };

    let app = Router::new()
        .fallback(fallback)
        .route("/", get(root))
        .with_state(state);

    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    Server::bind(&addr)
        .serve(app.into_make_service())
        .await
        .unwrap();

    Ok(())
}

/////////////////////////////////////////////////////////////////////////////
// Config
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, serde::Deserialize)]
struct Config {
    #[serde(default = "Config::default_port")]
    port: u16,
    db_dir: Option<String>,
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
// Error
/////////////////////////////////////////////////////////////////////////////

// A slightly revised version of the official example
// https://github.com/tokio-rs/axum/blob/v0.6.x/examples/anyhow-error-response/src/main.rs

enum WebError {
    AnyhowError(anyhow::Error),
    Status((StatusCode, String)),
}

// Tell axum how to convert `AppError` into a response.
impl IntoResponse for WebError {
    fn into_response(self) -> Response {
        match self {
            WebError::AnyhowError(e) => {
                error!("Something went wrong: {}", e);
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    format!("Something went wrong: {}", e),
                )
                    .into_response()
            }
            WebError::Status(status) => status.into_response(),
        }
    }
}

// This enables using `?` on functions that return `Result<_, anyhow::Error>` to turn them into
// `Result<_, WebError>`. That way you don't need to do that manually.
impl<E> From<E> for WebError
where
    E: Into<anyhow::Error>,
{
    fn from(err: E) -> Self {
        WebError::AnyhowError(err.into())
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

/////////////////////////////////////////////////////////////////////////////
// Handlers
/////////////////////////////////////////////////////////////////////////////

/// axum handler for any request that fails to match the router routes.
/// This implementation returns HTTP status code Not Found (404).
async fn fallback(uri: Uri) -> impl IntoResponse {
    (
        axum::http::StatusCode::NOT_FOUND,
        format!("No route {}", uri),
    )
}

async fn root() -> &'static str {
    "Hello, World!"
}
