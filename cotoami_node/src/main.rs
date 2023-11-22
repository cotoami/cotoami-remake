use anyhow::Result;
use cotoami_node::prelude::*;
use tracing::info;

#[tokio::main]
async fn main() -> Result<()> {
    // Install global collector configured based on RUST_LOG env var.
    tracing_subscriber::fmt::init();

    let config = Config::load_from_env()?;
    info!("Config loaded: {:?}", config);

    let (handle, _shutdown_trigger) = cotoami_node::launch_server(config).await?;
    handle.await??;

    Ok(())
}
