use anyhow::Result;
use cotoami_node::prelude::*;
use tracing::info;

#[tokio::main]
async fn main() -> Result<()> {
    // Install global collector configured based on RUST_LOG env var.
    tracing_subscriber::fmt::init();

    let node_config = NodeConfig::load_from_env()?;
    info!("NodeConfig loaded: {:?}", node_config);
    let node_state = NodeState::new(node_config).await?;
    node_state.server_conns().connect_all().await;

    let server_config = ServerConfig::load_from_env()?;
    info!("ServerConfig loaded: {:?}", server_config);

    let (handle, _shutdown_trigger) =
        cotoami_node::launch_server(server_config, node_state).await?;
    handle.await??;

    Ok(())
}
