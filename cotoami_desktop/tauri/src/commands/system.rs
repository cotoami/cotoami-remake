use std::{env, path::PathBuf};

use chrono::Local;
use tauri::{Manager, Result};

use crate::{commands::db::recent::RecentDatabases, message::MessageSink};

#[derive(serde::Serialize)]
pub struct SystemInfo {
    app_version: String,
    resource_dir: Option<String>,
    app_config_dir: Option<String>,
    app_data_dir: Option<String>,
    time_zone_offset_in_sec: i32,
    os: String,
    recent_databases: RecentDatabases,
}

#[tauri::command]
pub fn system_info(app_handle: tauri::AppHandle) -> SystemInfo {
    // tauri::PackageInfo
    // https://docs.rs/tauri/1.6.1/tauri/struct.PackageInfo.html
    let package_info = app_handle.package_info();
    let app_version = package_info.version.to_string();

    // tauri::PathResolver
    // https://docs.rs/tauri/1.6.1/tauri/struct.PathResolver.html
    let path_resolver = app_handle.path();
    let resource_dir = to_path_string(path_resolver.resource_dir(), &app_handle);
    let app_config_dir = to_path_string(path_resolver.app_config_dir(), &app_handle);
    let app_data_dir = to_path_string(path_resolver.app_data_dir(), &app_handle);

    let time_zone_offset_in_sec = Local::now().offset().local_minus_utc();

    let mut recent_databases = RecentDatabases::load(&app_handle);
    recent_databases.delete_invalid_folders(&app_handle);

    SystemInfo {
        app_version,
        resource_dir,
        app_config_dir,
        app_data_dir,
        time_zone_offset_in_sec,
        os: env::consts::OS.into(),
        recent_databases,
    }
}

fn to_path_string(path: Result<PathBuf>, app_handle: &tauri::AppHandle) -> Option<String> {
    match path {
        Ok(path) => path.to_str().map(str::to_string),
        Err(e) => {
            app_handle.error("Failed to get a path", Some(e));
            None
        }
    }
}
