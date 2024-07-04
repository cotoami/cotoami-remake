use std::{
    path::{self, Path, PathBuf},
    string::ToString,
    sync::Arc,
};

use cotoami_db::{Database, Node};
use cotoami_node::prelude::*;
use tauri::Manager;

use self::recent::RecentDatabases;
use crate::{
    commands::{error::Error, OperatingAs},
    config::Configs,
    log::Logger,
};

pub(crate) mod recent;

#[derive(serde::Serialize)]
pub struct DatabaseInfo {
    folder: String,
    initial_dataset: InitialDataset,
}

impl DatabaseInfo {
    async fn new(folder: String, node_state: &NodeState) -> Result<Self, Error> {
        let opr = node_state.local_node_as_operator()?;
        let initial_dataset = node_state.initial_dataset(Arc::new(opr)).await?;
        Ok(Self {
            folder,
            initial_dataset,
        })
    }

    pub fn local_node(&self) -> &Node {
        self.initial_dataset
            .local_node()
            .unwrap_or_else(|| unreachable!("The local node must exist in the node data."))
    }
}

#[tauri::command]
pub fn validate_new_database_folder(base_folder: String, folder_name: String) -> Result<(), Error> {
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
pub fn validate_database_folder(folder: &str) -> Result<(), Error> {
    let path = PathBuf::from(folder);
    if let Ok(Some((node, _))) = Database::try_read_node_info(path) {
        if node.has_root_cotonoma() {
            Ok(())
        } else {
            Err(Error::new(
                "invalid-node",
                "The database node must have a root cotonoma.",
            ))
        }
    } else {
        Err(Error::new(
            "invalid-database-folder",
            "Unable to find a database in the given folder.",
        ))
    }
}

#[tauri::command]
pub async fn create_database(
    app_handle: tauri::AppHandle,
    database_name: String,
    base_folder: String,
    folder_name: String,
) -> Result<DatabaseInfo, Error> {
    let path: PathBuf = [base_folder, folder_name].iter().collect();
    let folder = normalize_path(path)?;
    app_handle.debug("Creating a database...", Some(&folder));

    // Create a new config.
    let mut node_config = NodeConfig::new_standalone(Some(folder.clone()), Some(database_name));
    node_config.owner_password = Some(cotoami_db::generate_secret(None));

    // Create a node state with the config.
    let node_state = NodeState::new(node_config.clone()).await?;

    // Save the config.
    let mut configs = Configs::load(&app_handle);
    configs.insert(node_state.try_get_local_node_id()?, node_config);
    configs.save(&app_handle);

    // Operating as the local node.
    let operating_as = OperatingAs::default();
    operating_as.operate_as_local(node_state.clone(), app_handle.clone())?;
    app_handle.manage(operating_as);

    // DatabaseInfo
    let db_info = DatabaseInfo::new(folder.clone(), &node_state).await?;
    app_handle.info("Database created.", Some(&db_info.local_node().name));
    RecentDatabases::update(&app_handle, folder, db_info.local_node());

    // Store the state.
    app_handle.manage(node_state);

    Ok(db_info)
}

#[tauri::command]
pub async fn open_database(
    app_handle: tauri::AppHandle,
    database_folder: String,
) -> Result<DatabaseInfo, Error> {
    let folder = normalize_path(&database_folder)?;
    validate_database_folder(&folder)?;

    // Load or create a config.
    let node_config = if let Some((node, require_password)) = Database::try_read_node_info(&folder)?
    {
        let mut configs = Configs::load(&app_handle);
        let config = if let Some(config) = configs.get_mut(&node.uuid) {
            app_handle.debug("Found an existing config.", Some(&node.name));
            config.db_dir = Some(folder.clone());
            // sync just to avoid confusion, though `node_name` has an effect only in database creation.
            config.node_name = Some(node.name);
            config.clone()
        } else {
            let mut config = NodeConfig::new_standalone(Some(folder.clone()), Some(node.name));
            if require_password {
                unimplemented!("Need to display a modal to input a password here.");
            } else {
                config.owner_password = Some(cotoami_db::generate_secret(None));
                app_handle.debug("The owner password is going to be initialized.", None);
            }
            configs.insert(node.uuid, config.clone());
            config
        };
        configs.save(&app_handle);
        config
    } else {
        unreachable!();
    };

    // Reuse an existing state or create a new one.
    // TODO: Support opening another database. Currently, it will reuse an existing state
    //       whatever database it belongs.
    let node_state = match app_handle.try_state::<NodeState>() {
        Some(state) => {
            app_handle.debug("Reusing the existing NodeState.", None);
            state.inner().clone()
        }
        _ => {
            app_handle.debug("Creating a new NodeState.", None);
            let node_state = NodeState::new(node_config).await?;

            // Operating as the local node.
            let operating_as = OperatingAs::default();
            operating_as.operate_as_local(node_state.clone(), app_handle.clone())?;
            app_handle.manage(operating_as);

            app_handle.manage(node_state.clone());
            node_state
        }
    };

    // DatabaseInfo
    let db_info = DatabaseInfo::new(folder.clone(), &node_state).await?;
    app_handle.info("Database opened.", Some(&db_info.local_node().name));
    RecentDatabases::update(&app_handle, folder, db_info.local_node());

    Ok(db_info)
}

fn normalize_path<P: AsRef<Path>>(path: P) -> Result<String, Error> {
    let path = path::absolute(path).map_err(|e| Error::new("invalid-path", e.to_string()))?;
    path.to_str().map(str::to_string).ok_or(Error::new(
        "invalid-path",
        "The path contains invalid unicodes.",
    ))
}
