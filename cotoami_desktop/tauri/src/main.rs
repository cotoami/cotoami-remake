#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

use std::{path::PathBuf, string::ToString};

use cotoami_db::prelude::Node;
use cotoami_node::prelude::*;
use parking_lot::Mutex;

pub mod window_state;

fn main() {
    tauri::Builder::default()
        .plugin(window_state::Builder::default().build())
        .manage(AppState::new())
        .invoke_handler(tauri::generate_handler![
            system_info,
            validate_new_folder_path,
            create_database
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

#[derive(serde::Serialize)]
struct SystemInfo {
    app_version: String,
    app_config_dir: Option<String>,
    app_data_dir: Option<String>,
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

    SystemInfo {
        app_version,
        app_config_dir,
        app_data_dir,
    }
}

#[tauri::command]
fn validate_new_folder_path(base_folder: String, folder_name: String) -> Result<(), Error> {
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
async fn create_database(
    state: tauri::State<'_, AppState>,
    database_name: String,
    base_folder: String,
    folder_name: String,
) -> Result<Node, Error> {
    let db_dir = {
        let mut path = PathBuf::from(base_folder);
        path.push(folder_name);
        path.to_str().map(str::to_string)
    };

    let node_config = NodeConfig::new_standalone(db_dir, Some(database_name));
    //let new_node_state = NodeState::new(node_config).await?;
    //state.inner().node_state.lock().replace(new_node_state);

    unimplemented!()
}
