use std::{
    path::{self, Path, PathBuf},
    string::ToString,
    sync::Arc,
};

use cotoami_db::prelude::*;
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
    local_node_id: Id<Node>,
    initial_dataset: InitialDataset, // could be from a remote node
    new_owner_password: Option<String>,
}

impl DatabaseInfo {
    pub async fn new(
        folder: String,
        node_state: &NodeState,
        operating_as: &OperatingAs,
        new_owner_password: Option<String>,
    ) -> Result<Self, Error> {
        Ok(Self {
            folder,
            local_node_id: node_state.try_get_local_node_id()?,
            initial_dataset: initial_dataset(node_state, operating_as).await?,
            new_owner_password,
        })
    }

    pub fn local_node(&self) -> &Node {
        self.initial_dataset
            .node(&self.local_node_id)
            .unwrap_or_else(|| unreachable!("The local node must exist in the dataset."))
    }
}

pub(crate) async fn initial_dataset(
    node_state: &NodeState,
    operating_as: &OperatingAs,
) -> Result<InitialDataset, Error> {
    if let Some(parent_id) = operating_as.parent_id() {
        let parent_service = node_state.parent_services().try_get(&parent_id)?;
        parent_service.initial_dataset().await.map_err(Error::from)
    } else {
        let opr = node_state.local_node_as_operator()?;
        node_state
            .initial_dataset(Arc::new(opr))
            .await
            .map_err(Error::from)
    }
}

#[tauri::command]
pub fn validate_new_database_folder(
    base_folder: String,
    folder_name: String,
) -> Result<String, Error> {
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
        Ok(false) => Ok(path.to_string_lossy().to_string()),
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
    let new_owner_password = cotoami_db::generate_secret(None);
    let mut node_config = NodeConfig::new_standalone(Some(folder.clone()), Some(database_name));
    node_config.owner_password = Some(new_owner_password.clone());

    // Create a node state with the config.
    let node_state = NodeState::new(node_config.clone()).await?;

    // Save the config.
    let mut configs = Configs::load(&app_handle);
    configs.insert(node_state.try_get_local_node_id()?, node_config);
    configs.save(&app_handle);

    // Operating as the local node.
    let operating_as = OperatingAs::default();
    operating_as.operate_as_local(node_state.clone(), app_handle.clone())?;

    // DatabaseInfo
    let db_info = DatabaseInfo::new(
        folder.clone(),
        &node_state,
        &operating_as,
        Some(new_owner_password),
    )
    .await?;
    app_handle.info("Database created.", Some(&folder));
    RecentDatabases::update(&app_handle, folder, db_info.local_node());

    // Store the states.
    app_handle.manage(node_state);
    app_handle.manage(operating_as);

    Ok(db_info)
}

#[tauri::command]
pub async fn open_database(
    app_handle: tauri::AppHandle,
    database_folder: String,
    owner_password: Option<String>,
) -> Result<DatabaseInfo, Error> {
    // Read node infro from the given folder.
    let folder = normalize_path(&database_folder)?;
    validate_database_folder(&folder)?;
    let Some((node, require_password)) = Database::try_read_node_info(&folder)? else {
        unreachable!(); // Since the database folder has been vailidated.
    };

    // Load or create a config.
    let mut new_owner_password = None;
    let mut configs = Configs::load(&app_handle);

    let node_config = if let Some(config) = configs.get_mut(&node.uuid) {
        config.db_dir = Some(folder.clone());

        // Although the config `node_name` has an effect only in database creation,
        // update it to the actual name to avoid confusion.
        config.node_name = Some(node.name);

        // Override the owner password with the given one
        if let Some(password) = owner_password {
            config.owner_password = Some(password);
        }

        config.clone()
    } else {
        let mut config = NodeConfig::new_standalone(Some(folder.clone()), Some(node.name));
        if require_password {
            if let Some(password) = owner_password {
                // Set the given password to authenticate
                config.owner_password = Some(password);
            } else {
                // Missing password
                return Err(Error::invalid_owner_password());
            }
        } else {
            // Configure a new owner password
            new_owner_password = Some(cotoami_db::generate_secret(None));
            config.owner_password = new_owner_password.clone();
        }
        configs.insert(node.uuid, config.clone());
        config
    };

    configs.save(&app_handle);

    // Reuse an existing state (on reloading) or create a new one.
    // TODO: Support opening another database. Currently, it will reuse an existing
    //       state whatever database it belongs to.
    let node_state = match app_handle.try_state::<NodeState>() {
        Some(state) => state.inner().clone(),
        None => {
            let node_state = match NodeState::new(node_config).await {
                Ok(node_state) => node_state,
                Err(error) => match error.downcast_ref::<NodeError>() {
                    Some(NodeError::OwnerAuthenticationFailed) => {
                        return Err(Error::invalid_owner_password())
                    }
                    _ => return Err(error.into()),
                },
            };

            // Update RecentDatabases
            let local_node = node_state.local_node().await?;
            RecentDatabases::update(&app_handle, folder.clone(), &local_node);

            app_handle.manage(node_state.clone());
            node_state
        }
    };

    // Init OperatingAs
    if app_handle.try_state::<OperatingAs>().is_none() {
        let operating_as = OperatingAs::default();
        operating_as.operate_as_local(node_state.clone(), app_handle.clone())?;
        app_handle.manage(operating_as);
    };

    // DatabaseInfo
    let db_info = DatabaseInfo::new(
        folder.clone(),
        &node_state,
        &app_handle.state::<OperatingAs>(),
        new_owner_password,
    )
    .await?;
    app_handle.info("Database opened.", Some(&folder));

    Ok(db_info)
}

fn normalize_path<P: AsRef<Path>>(path: P) -> Result<String, Error> {
    let path = path::absolute(path).map_err(|e| Error::new("invalid-path", e.to_string()))?;
    path.to_str().map(str::to_string).ok_or(Error::new(
        "invalid-path",
        "The path contains invalid unicodes.",
    ))
}
