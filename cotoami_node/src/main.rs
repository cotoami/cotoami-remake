use anyhow::Result;

#[tokio::main]
async fn main() -> Result<()> {
    // Install global collector configured based on RUST_LOG env var.
    tracing_subscriber::fmt::init();
    let (handle, _shutdown_trigger) = cotoami_node::run_server().await?;
    handle.await??;
    Ok(())
}
