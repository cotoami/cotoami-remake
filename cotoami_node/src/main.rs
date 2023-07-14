use anyhow::Result;
use axum::{
    http::StatusCode,
    http::Uri,
    response::{IntoResponse, Response},
    routing::get,
    Router, Server,
};
use dotenvy::dotenv;
use std::net::SocketAddr;
use tracing::{error, info};

pub mod pubsub;

#[tokio::main]
async fn main() -> Result<()> {
    // Install global collector configured based on RUST_LOG env var.
    tracing_subscriber::fmt::init();

    let config = Config::load()?;
    info!("Config loaded: {:?}", config);

    let app = Router::new().fallback(fallback).route("/", get(root));

    let addr = SocketAddr::from(([0, 0, 0, 0], config.port));
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
}

impl Config {
    const ENV_PREFXI: &str = "COTOAMI_";

    fn load() -> Result<Config, envy::Error> {
        dotenv().ok();
        envy::prefixed(Self::ENV_PREFXI).from_env::<Config>()
    }

    // Functions returning a default value as a workaround for the issue:
    // https://github.com/serde-rs/serde/issues/368
    fn default_port() -> u16 {
        5103
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
