use std::sync::Arc;

use cotoami_db::prelude::*;
use cotoami_node::prelude::*;
use parking_lot::RwLock;

use self::error::Error;

pub mod conn;
pub mod db;
pub mod error;
pub mod system;

#[derive(Default)]
pub struct OperatingAs(RwLock<Option<Id<Node>>>);

impl OperatingAs {
    pub fn node_id(&self) -> Option<Id<Node>> { *self.0.read() }

    pub fn set(&self, node_id: Option<Id<Node>>) { *self.0.write() = node_id }
}

#[tauri::command]
pub async fn node_command(
    state: tauri::State<'_, NodeState>,
    operating_as: tauri::State<'_, OperatingAs>,
    command: Command,
) -> Result<String, Error> {
    let node_state = state.inner();

    let mut request = command.into_request();
    request.set_accept(SerializeFormat::Json);

    let response = if let Some(node_id) = operating_as.node_id() {
        // Send the request to the operating node
        let parent_service = state.parent_services().try_get(&node_id)?;
        request.operate_as_owner();
        parent_service.call(request).await?
    } else {
        // Send the request to the local node
        request.set_from(Arc::new(node_state.local_node_as_operator()?));
        node_state.call(request).await?
    };

    // Return the result as a JSON string
    response.json().map_err(Error::from)
}
