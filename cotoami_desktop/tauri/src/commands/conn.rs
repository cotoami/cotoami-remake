use cotoami_node::prelude::*;

use crate::{commands::error::Error, log::Logger};

#[tauri::command]
pub async fn connect_to_servers(
    app_handle: tauri::AppHandle,
    state: tauri::State<'_, NodeState>,
) -> Result<(), Error> {
    let node_state = state.inner();
    app_handle.debug("Connecting to the servers.", None);
    node_state.server_conns().connect_all().await;
    Ok(())
}
