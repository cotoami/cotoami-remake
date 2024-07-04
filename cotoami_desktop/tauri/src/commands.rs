use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use cotoami_node::{prelude::*, Abortables};
use parking_lot::RwLock;
use tauri::AppHandle;

use self::error::Error;
use crate::event;

pub mod conn;
pub mod db;
pub mod error;
pub mod system;

#[tauri::command]
pub async fn node_command(
    state: tauri::State<'_, NodeState>,
    operating_as: tauri::State<'_, OperatingAs>,
    command: Command,
) -> Result<String, Error> {
    let node_state = state.inner();

    let mut request = command.into_request();
    request.set_accept(SerializeFormat::Json);

    let response = if let Some(parent_id) = operating_as.parent_id() {
        // Send the request to the operating node
        let parent_service = state.parent_services().try_get(&parent_id)?;
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

#[derive(Default)]
pub struct OperatingAs {
    parent_id: RwLock<Option<Id<Node>>>,
    piping_events: Abortables,
}

impl OperatingAs {
    pub fn parent_id(&self) -> Option<Id<Node>> { *self.parent_id.read() }

    pub fn operate_as_local(&self, state: NodeState, app_handle: AppHandle) -> Result<()> {
        self.operate_as(None, state, app_handle)
    }

    pub fn operate_as(
        &self,
        parent_id: Option<Id<Node>>,
        state: NodeState,
        app_handle: AppHandle,
    ) -> Result<()> {
        if let Some(parent_id) = parent_id {
            // Check parent
        }

        // Replace piping processes.
        self.piping_events.abort_all();
        tokio::spawn(event::pipe(
            parent_id,
            state,
            app_handle,
            self.piping_events.clone(),
        ));

        *self.parent_id.write() = parent_id;
        Ok(())
    }
}
