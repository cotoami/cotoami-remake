use anyhow::Result;
use cotoami_node::prelude::*;
use tracing::info;

#[tokio::main]
async fn main() -> Result<()> {
    // Install global collector configured based on RUST_LOG env var.
    tracing_subscriber::fmt::init();

    let config = NodeConfig::load_from_env()?;
    info!("Config loaded: {:?}", config);

    let server_config = ServerConfig::load_from_env()?;
    info!("ServerConfig loaded: {:?}", server_config);

    let (handle, _shutdown_trigger) = cotoami_node::launch_server(server_config, config).await?;
    handle.await??;

    Ok(())
}
