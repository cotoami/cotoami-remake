use std::{path::PathBuf, string::ToString, sync::Arc};

use anyhow::anyhow;
use cotoami_db::{Database, Id, Node};
use cotoami_node::prelude::*;
use tauri::Manager;

use crate::{error::Error, event, log::Logger, recent::RecentDatabases};

#[derive(serde::Serialize)]
pub(crate) struct DatabaseInfo {
    folder: String,
    last_change_number: i64,
    nodes: Vec<Node>,
    local_node_id: Id<Node>,
    parent_node_ids: Vec<Id<Node>>,
    servers: Vec<Server>,
}

impl DatabaseInfo {
    async fn new(folder: String, node_state: &NodeState) -> Result<Self, Error> {
        let opr = Arc::new(node_state.local_node_as_operator()?);
        Ok(Self {
            folder,
            // Get the last change number before retrieving database contents to
            // ensure those contents to be the same or newer than the revision of the number.
            // It should be no problem to have newer contents than the change number as long as
            // each change is idempotent.
            last_change_number: node_state
                .last_change_number()
                .await?
                .unwrap_or_else(|| unreachable!("There must be changes before.")),
            nodes: node_state.all_nodes().await?,
            local_node_id: node_state.db().globals().local_node_id()?,
            parent_node_ids: node_state.db().globals().parent_node_ids(),
            servers: node_state.all_server_nodes(opr).await?,
        })
    }

    fn local_node(&self) -> &Node {
        self.nodes
            .iter()
            .find(|node| node.uuid == self.local_node_id)
            .unwrap_or_else(|| unreachable!("The local node must exist in the node data."))
    }
}

#[tauri::command]
pub(crate) fn validate_new_database_folder(
    base_folder: String,
    folder_name: String,
) -> Result<(), Error> {
    let mut path = PathBuf::from(base_folder);
    if !path.is_dir() {
        return Err(Error::new(
            "non-existent-base-folder",
            "The base folder doesn't exist.",
        ));
    }
    path.push(folder_name);
    match path.try_exists() {
        Ok(true) => Err(Error::new(
            "folder-already-exists",
            "The folder already exists.",
        )),
        Ok(false) => Ok(()),
        Err(e) => Err(Error::new("file-system-error", e.to_string())),
    }
}

#[tauri::command]
pub(crate) fn validate_database_folder(database_folder: String) -> Result<(), Error> {
    let path = PathBuf::from(database_folder);
    if Database::is_in(path) {
        Ok(())
    } else {
        Err(Error::new(
            "invalid-database-folder",
            "Unable to find a database in the given folder.",
        ))
    }
}

#[tauri::command]
pub(crate) async fn create_database(
    app_handle: tauri::AppHandle,
    database_name: String,
    base_folder: String,
    folder_name: String,
) -> Result<DatabaseInfo, Error> {
    let folder = {
        let path: PathBuf = [base_folder, folder_name].iter().collect();
        path.to_str()
            .map(str::to_string)
            .ok_or(anyhow!("Invalid folder path: {:?}", path))?
    };
    app_handle.debug("Creating a database...", Some(&folder));

    let node_config = NodeConfig::new_standalone(Some(folder.clone()), Some(database_name));
    let node_state = NodeState::new(node_config).await?;
    tokio::spawn(event::listen(node_state.clone(), app_handle.clone()));

    let db_info = DatabaseInfo::new(folder.clone(), &node_state).await?;
    app_handle.info(
        &format!("Database [{}] created.", db_info.local_node().name),
        None,
    );
    RecentDatabases::update(&app_handle, folder, db_info.local_node());

    app_handle.manage(node_state);

    Ok(db_info)
}

#[tauri::command]
pub(crate) async fn open_database(
    app_handle: tauri::AppHandle,
    database_folder: String,
) -> Result<DatabaseInfo, Error> {
    let folder = PathBuf::from(&database_folder)
        .to_str()
        .map(str::to_string)
        .ok_or(anyhow!("Invalid folder path: {}", database_folder))?;
    validate_database_folder(folder.clone())?;

    let node_config = NodeConfig::new_standalone(Some(folder.clone()), None);
    let node_state = NodeState::new(node_config).await?;
    tokio::spawn(event::listen(node_state.clone(), app_handle.clone()));

    let db_info = DatabaseInfo::new(folder.clone(), &node_state).await?;
    RecentDatabases::update(&app_handle, folder, db_info.local_node());

    app_handle.manage(node_state);

    Ok(db_info)
}

#[tauri::command]
pub(crate) async fn node_command(
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
