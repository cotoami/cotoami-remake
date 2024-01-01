use std::net::SocketAddr;

use anyhow::Result;
use futures::TryFutureExt;
use tokio::{
    sync::{oneshot, oneshot::Sender},
    task::JoinHandle,
};

use crate::state::{Config, NodeState};

mod client;
mod event;
mod pubsub;
mod service;
mod state;
mod web;

pub mod prelude {
    pub use crate::state::{Config, NodeState};
}

pub async fn launch_server(config: Config) -> Result<(JoinHandle<Result<()>>, Sender<()>)> {
    let port = config.port;

    // Build Web API service
    let state = NodeState::new(config)?;
    state.init().await?;
    let web_api = web::router(state);

    // Deploy the service to a hyper Server
    let addr = SocketAddr::from(([0, 0, 0, 0], port));
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
