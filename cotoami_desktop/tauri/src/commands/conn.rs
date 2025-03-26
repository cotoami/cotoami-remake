use cotoami_node::prelude::*;
use tracing::debug;

use crate::commands::error::Error;

#[tauri::command]
pub async fn connect_to_servers(state: tauri::State<'_, NodeState>) -> Result<(), Error> {
    debug!("Connecting to the servers...");
    let node_state = state.inner();
    node_state.server_conns().connect_all().await;
    Ok(())
}
