#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

use std::{path::PathBuf, string::ToString};

use anyhow::anyhow;
use cotoami_db::prelude::{Database, Node};
use cotoami_node::prelude::*;
use parking_lot::Mutex;

use crate::{log::Logger, recent::RecentDatabases};

mod log;
mod recent;
mod window_state;

fn main() {
    tauri::Builder::default()
        .plugin(window_state::Builder::default().build())
        .manage(AppState::new())
        .invoke_handler(tauri::generate_handler![
            system_info,
            validate_new_database_folder,
            validate_database_folder,
            create_database,
            open_database
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

struct AppState {
    node_state: Mutex<Option<NodeState>>,
}

impl AppState {
    fn new() -> Self {
        Self {
            node_state: Mutex::new(None),
        }
    }
}

#[derive(serde::Serialize)]
struct Error {
    code: String,
    message: String,
    details: Option<String>,
}

impl Error {
    fn new(code: impl Into<String>, message: impl Into<String>) -> Self {
        Self {
            code: code.into(),
            message: message.into(),
            details: None,
        }
    }

    fn add_details(mut self, details: impl Into<String>) -> Self {
        self.details = Some(details.into());
        self
    }
}

impl From<anyhow::Error> for Error {
    fn from(e: anyhow::Error) -> Self { Error::new("system-error", e.to_string()) }
}

// TODO: write thorough conversion
impl From<ServiceError> for Error {
    fn from(e: ServiceError) -> Self {
        Error::new("service-error", "Service Error").add_details(format!("{:?}", e))
    }
}

#[derive(serde::Serialize)]
struct SystemInfo {
    app_version: String,
    app_config_dir: Option<String>,
    app_data_dir: Option<String>,
    recent_databases: RecentDatabases,
}

#[tauri::command]
fn system_info(app_handle: tauri::AppHandle) -> SystemInfo {
    // tauri::PackageInfo
    // https://docs.rs/tauri/1.6.1/tauri/struct.PackageInfo.html
    let package_info = app_handle.package_info();
    let app_version = package_info.version.to_string();

    // tauri::PathResolver
    // https://docs.rs/tauri/1.6.1/tauri/struct.PathResolver.html
    let path_resolver = app_handle.path_resolver();
    let app_config_dir = path_resolver
        .app_config_dir()
        .and_then(|path| path.to_str().map(str::to_string));
    let app_data_dir = path_resolver
        .app_data_dir()
        .and_then(|path| path.to_str().map(str::to_string));

    let recent_databases = RecentDatabases::load(&app_handle);

    SystemInfo {
        app_version,
        app_config_dir,
        app_data_dir,
        recent_databases,
    }
}

#[tauri::command]
fn validate_new_database_folder(base_folder: String, folder_name: String) -> Result<(), Error> {
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
fn validate_database_folder(database_folder: String) -> Result<(), Error> {
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
async fn create_database(
    app_handle: tauri::AppHandle,
    state: tauri::State<'_, AppState>,
    database_name: String,
    base_folder: String,
    folder_name: String,
) -> Result<Node, Error> {
    let folder = {
        let path: PathBuf = [base_folder, folder_name].iter().collect();
        path.to_str()
            .map(str::to_string)
            .ok_or(anyhow!("Invalid folder path: {:?}", path))?
    };
    app_handle.debug(format!("Creating a database..."), Some(folder.clone()));

    let node_config = NodeConfig::new_standalone(Some(folder.clone()), Some(database_name));
    let node_state = NodeState::new(node_config).await?;
    let local_node = node_state.local_node().await?;
    app_handle.info(format!("Database [{}] created.", local_node.name), None);

    state.inner().node_state.lock().replace(node_state);

    RecentDatabases::update(&app_handle, folder, &local_node);

    Ok(local_node)
}

#[tauri::command]
async fn open_database(
    app_handle: tauri::AppHandle,
    state: tauri::State<'_, AppState>,
    database_folder: String,
) -> Result<Node, Error> {
    let folder = PathBuf::from(&database_folder)
        .to_str()
        .map(str::to_string)
        .ok_or(anyhow!("Invalid folder path: {}", database_folder))?;
    validate_database_folder(folder.clone())?;

    let node_config = NodeConfig::new_standalone(Some(folder.clone()), None);
    let node_state = NodeState::new(node_config).await?;
    let local_node = node_state.local_node().await?;

    state.inner().node_state.lock().replace(node_state);

    RecentDatabases::update(&app_handle, folder, &local_node);

    Ok(local_node)
}
