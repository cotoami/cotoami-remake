use std::net::SocketAddr;

use anyhow::Result;
use tokio::{
    sync::{oneshot, oneshot::Sender},
    task::JoinHandle,
};

use crate::state::{Config, NodeState};

mod client;
mod pubsub;
mod service;
mod state;
mod web;

pub mod prelude {
    pub use crate::state::{Config, NodeState};
}

pub async fn launch_server(config: Config) -> Result<(JoinHandle<Result<()>>, Sender<()>)> {
    let port = config.port;

    let state = NodeState::new(config)?;
    state.prepare().await?;

    let router = web::router(state);

    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    let server = axum::Server::bind(&addr).serve(router.into_make_service());

    let (tx, rx) = oneshot::channel::<()>();
    let server = server.with_graceful_shutdown(async {
        rx.await.ok();
    });

    let handle = tokio::spawn(async move {
        server.await?;
        Ok(())
    });

    Ok((handle, tx))
}
