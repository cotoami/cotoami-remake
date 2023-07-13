use anyhow::Result;
use axum::{http::Uri, response::IntoResponse, routing::get, Router, Server};
use std::net::{IpAddr, Ipv4Addr, SocketAddr};

const DEFAULT_PORT: u16 = 5103;

#[tokio::main]
async fn main() -> Result<()> {
    env_logger::init(); // ex. export RUST_LOG=debug

    let app = Router::new().fallback(fallback).route("/", get(root));

    let socket = SocketAddr::new(IpAddr::V4(Ipv4Addr::new(0, 0, 0, 0)), DEFAULT_PORT);
    Server::bind(&socket)
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
