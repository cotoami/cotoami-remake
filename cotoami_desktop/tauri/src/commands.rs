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

#[tauri::command]
pub async fn operate_as(
    app_handle: tauri::AppHandle,
    state: tauri::State<'_, NodeState>,
    operating_as: tauri::State<'_, OperatingAs>,
    parent_id: Option<Id<Node>>,
) -> Result<InitialDataset, Error> {
    let node_state = state.inner();

    // Switch the operating node.
    operating_as.operate_as(parent_id, node_state.clone(), app_handle)?;

    // Fetch the [InitialDataset] from the new operating node.
    if let Some(parent_id) = parent_id {
        let parent_service = state.parent_services().try_get(&parent_id)?;
        parent_service.initial_dataset().await.map_err(Error::from)
    } else {
        let opr = node_state.local_node_as_operator()?;
        node_state
            .initial_dataset(Arc::new(opr))
            .await
            .map_err(Error::from)
    }
}

#[derive(Default)]
pub struct OperatingAs {
    parent_id: RwLock<Option<Id<Node>>>,
    piping_events: Abortables,
}

impl OperatingAs {
    pub fn parent_id(&self) -> Option<Id<Node>> { *self.parent_id.read() }

    pub fn operate_as_local(&self, state: NodeState, app_handle: AppHandle) -> Result<(), Error> {
        self.operate_as(None, state, app_handle)
    }

    pub fn operate_as(
        &self,
        parent_id: Option<Id<Node>>,
        state: NodeState,
        app_handle: AppHandle,
    ) -> Result<(), Error> {
        // Check the privilege to operate the remote node.
        if let Some(parent_id) = parent_id {
            match state.local_as_child(&parent_id) {
                Some(ChildNode { as_owner: true, .. }) => (),
                _ => {
                    return Err(Error::new(
                        "permission-error",
                        "The local node does not have owner privilege of the target node.",
                    ))
                }
            }
        }

        // Initialize piping processes.
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
