use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use cotoami_node::{prelude::*, Abortables};
use parking_lot::RwLock;
use tauri::AppHandle;
use tracing::debug;

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
    debug!("node_command: {command:?}");

    let mut request = command.into_request();
    request.set_accept(SerializeFormat::Json);

    let response = if let Some(parent_id) = operating_as.parent_id() {
        // Send the request to the operating node
        let parent_service = state.parent_services().try_get(&parent_id)?;
        request.operate_as_owner();
        parent_service.call(request).await?
    } else {
        // Send the request to the local node
        request.set_from(Arc::new(state.local_node_as_operator()?));
        state.call(request).await?
    };

    // Return the result as a JSON string
    response.json().map_err(Error::from)
}

/// An tauri command that is the entry point to the Operate-as feature.
///
/// The Operate-as feature allows to switch the operated node between the
/// local node (default) and one of the parent nodes to which the local node
/// has owner privilege.
///
/// This feature is implemented by the following elements:
/// * This tauri command: [operate_as].
/// * The [OperatingAs] struct which holds the ID of the current operating node,
///   and tasks piping events from the node to the frontend. It is stored in
///   [tauri::State] and modified from the [operate_as] command.
/// * [event::pipe] spawns tasks piping each [ChangelogEntry] and [LocalNodeEvent]
///   from the operating node to the frontend.
/// * An [InitialDataset] will be fetched when the operating node is switched.
///   [commands::db::initial_dataset] returns an [InitialDataset] according to
///   the current [OperatingAs].
/// * The tauri command [node_command] changes the destination of [Command]s
///   according to the current [OperatingAs]. If the destination is one of the
///   parent nodes, it sets [Request::as_owner] to true so that the [Operator]
///   of requests will be replaced with the owner of the parent.
///   (cf. [cotoami_node::event::remote::handle_event_from_operator])
/// * In a REST/SSE environment, [Request::as_owner] will be translated as a
///   `x-cotoami-operate-as-owner` header. (cf. [cotoami_node::web::require_operator])
#[tauri::command]
pub async fn operate_as(
    app_handle: tauri::AppHandle,
    state: tauri::State<'_, NodeState>,
    operating_as: tauri::State<'_, OperatingAs>,
    parent_id: Option<Id<Node>>,
) -> Result<InitialDataset, Error> {
    // Switch the operating node.
    debug!("Switching the operating node to {parent_id:?}...");
    operating_as.operate_as(parent_id, state.inner().clone(), app_handle)?;

    // Fetch the [InitialDataset] from the new operating node.
    db::initial_dataset(&state, &operating_as).await
}

#[derive(Default)]
pub struct OperatingAs {
    /// The ID of the parent node that the application is currently operating.
    /// [None] means the application is operating the local node (default).
    parent_id: RwLock<Option<Id<Node>>>,

    /// Tasks to pipe operating node events into the frontend.
    piping_events: Abortables,
}

impl OperatingAs {
    pub fn parent_id(&self) -> Option<Id<Node>> { *self.parent_id.read() }

    pub fn operating_remote(&self) -> bool { self.parent_id.read().is_some() }

    pub fn operate_as_local(&self, state: NodeState, app_handle: AppHandle) -> Result<(), Error> {
        self.operate_as(None, state, app_handle)
    }

    /// Switch the operated node to the specified parent node if the local
    /// node has the permissions to do so.
    ///
    /// If `parent_id` is [None], the operated node will be switched back to
    /// the local node.
    pub fn operate_as(
        &self,
        parent_id: Option<Id<Node>>,
        state: NodeState,
        app_handle: AppHandle,
    ) -> Result<(), Error> {
        // Check the privilege to operate the remote node.
        if let Some(parent_id) = parent_id {
            match state.child_privileges(&parent_id) {
                Some(ChildNode { as_owner: true, .. }) => (),
                _ => {
                    return Err(Error::new(
                        "permission-error",
                        "The local node does not have owner privilege to the target node.",
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
