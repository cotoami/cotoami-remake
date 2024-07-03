use std::sync::Arc;

use cotoami_node::prelude::*;

use self::error::Error;

pub mod conn;
pub mod db;
pub mod error;
pub mod system;

#[tauri::command]
pub async fn node_command(
    state: tauri::State<'_, NodeState>,
    command: Command,
) -> Result<String, Error> {
    let node_state = state.inner();

    // Build a request
    let mut request = command.into_request();
    request.set_from(Arc::new(node_state.local_node_as_operator()?));
    request.set_accept(SerializeFormat::Json);

    // Send the request to the local node
    let response = node_state.call(request).await?;

    // Return the result as a JSON string
    response.json().map_err(Error::from)
}
