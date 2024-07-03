use std::env;

use chrono::Local;

use crate::commands::db::recent::RecentDatabases;

#[derive(serde::Serialize)]
pub struct SystemInfo {
    app_version: String,
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
    let path_resolver = app_handle.path_resolver();
    let app_config_dir = path_resolver
        .app_config_dir()
        .and_then(|path| path.to_str().map(str::to_string));
    let app_data_dir = path_resolver
        .app_data_dir()
        .and_then(|path| path.to_str().map(str::to_string));

    let time_zone_offset_in_sec = Local::now().offset().local_minus_utc();

    let mut recent_databases = RecentDatabases::load(&app_handle);
    recent_databases.delete_invalid_folders(&app_handle);

    SystemInfo {
        app_version,
        app_config_dir,
        app_data_dir,
        time_zone_offset_in_sec,
        os: env::consts::OS.into(),
        recent_databases,
    }
}
