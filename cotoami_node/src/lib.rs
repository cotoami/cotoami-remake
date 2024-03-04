use std::{net::SocketAddr, sync::Arc};

use anyhow::Result;
use futures::TryFutureExt;
use tokio::{
    sync::{oneshot, oneshot::Sender},
    task::JoinHandle,
};

use crate::{
    state::{Config, NodeState},
    web::ServerConfig,
};

mod client;
mod event;
mod pubsub;
mod service;
mod state;
mod web;

pub mod prelude {
    pub use crate::{
        state::{Config, NodeState},
        web::ServerConfig,
    };
}

pub async fn launch_server(
    server_config: ServerConfig,
    config: Config,
) -> Result<(JoinHandle<Result<()>>, Sender<()>)> {
    // Build a Web API server
    let state = NodeState::new(config).await?;
    let addr = SocketAddr::from(([0, 0, 0, 0], server_config.port));
    let web_api = web::router(Arc::new(server_config), state);
    let server = axum::Server::bind(&addr).serve(web_api.into_make_service());

    // Prepare a way to gracefully shutdown a server
    // https://hyper.rs/guides/0.14/server/graceful-shutdown/
    let (tx, rx) = oneshot::channel::<()>();
    let server = server.with_graceful_shutdown(async {
        rx.await.ok();
    });

    // Launch the server
    Ok((tokio::spawn(server.map_err(anyhow::Error::from)), tx))
}
