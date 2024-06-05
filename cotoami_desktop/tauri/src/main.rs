#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

use std::{collections::HashMap, env, path::PathBuf, string::ToString, sync::Arc};

use anyhow::anyhow;
use chrono::Local;
use cotoami_db::{Database, Id, Node};
use cotoami_node::prelude::*;
use serde_json::value::Value;
use tauri::Manager;

use crate::{log::Logger, recent::RecentDatabases};

mod event;
mod log;
mod recent;
mod window_state;

fn main() {
    tauri::Builder::default()
        .plugin(window_state::Builder::default().build())
        .invoke_handler(tauri::generate_handler![
            system_info,
            validate_new_database_folder,
            validate_database_folder,
            create_database,
            open_database,
            node_command
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

#[derive(serde::Serialize)]
struct Error {
    code: String,
    default_message: String,
    params: HashMap<String, Value>,
}

impl Error {
    fn new(code: impl Into<String>, message: impl Into<String>) -> Self {
        Self {
            code: code.into(),
            default_message: message.into(),
            params: HashMap::new(),
        }
    }

    // TODO: write thorough conversion
    fn from_service_error(e: ServiceError) -> Self {
        match e {
            ServiceError::Request(e) => e.into(),
            ServiceError::Permission => Error::new("permission-error", "Permission error."),
            ServiceError::NotFound(msg) => Error::new(
                "not-found",
                msg.unwrap_or("The requested resource is not found.".into()),
            ),
            ServiceError::Server(msg) => Error::new("server-error", msg),
            _ => Error::new("service-error", format!("{e:?}")),
        }
    }

    fn system_error(message: impl Into<String>) -> Self {
        Error::new("system-error", message.into())
    }
}

impl From<anyhow::Error> for Error {
    fn from(e: anyhow::Error) -> Self {
        match e.downcast::<BackendServiceError>() {
            Ok(BackendServiceError(service_error)) => Self::from_service_error(service_error),
            Err(e) => Self::system_error(e.to_string()),
        }
    }
}

impl From<ServiceError> for Error {
    fn from(e: ServiceError) -> Self { Self::from_service_error(e) }
}

impl From<RequestError> for Error {
    fn from(e: RequestError) -> Self {
        Self {
            code: e.code,
            default_message: e.default_message,
            params: e.params,
        }
    }
}

#[derive(serde::Serialize)]
struct SystemInfo {
    app_version: String,
    app_config_dir: Option<String>,
    app_data_dir: Option<String>,
    time_zone_offset_in_sec: i32,
    os: String,
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

    let time_zone_offset_in_sec = Local::now().offset().local_minus_utc();

    let recent_databases = RecentDatabases::load(&app_handle);

    SystemInfo {
        app_version,
        app_config_dir,
        app_data_dir,
        time_zone_offset_in_sec,
        os: env::consts::OS.into(),
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

#[derive(serde::Serialize)]
struct DatabaseInfo {
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
async fn create_database(
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
async fn open_database(
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
async fn node_command(
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
