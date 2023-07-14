use anyhow::Result;
use axum::{http::Uri, response::IntoResponse, routing::get, Router, Server};
use std::net::SocketAddr;

const DEFAULT_PORT: u16 = 5103;

#[tokio::main]
async fn main() -> Result<()> {
    // install global collector configured based on RUST_LOG env var.
    tracing_subscriber::fmt::init();

    let app = Router::new().fallback(fallback).route("/", get(root));

    let addr = SocketAddr::from(([0, 0, 0, 0], DEFAULT_PORT));
    Server::bind(&addr)
        .serve(app.into_make_service())
        .await
        .unwrap();

    Ok(())
}

/// axum handler for any request that fails to match the router routes.
/// This implementation returns HTTP status code Not Found (404).
pub async fn fallback(uri: Uri) -> impl IntoResponse {
    (
        axum::http::StatusCode::NOT_FOUND,
        format!("No route {}", uri),
    )
}

async fn root() -> &'static str {
    "Hello, World!"
}
