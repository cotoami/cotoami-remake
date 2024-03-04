#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

use std::{path::PathBuf, string::ToString};

pub mod window_state;

fn main() {
    tauri::Builder::default()
        .plugin(window_state::Builder::default().build())
        .invoke_handler(tauri::generate_handler![
            system_info,
            validate_new_folder_path
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

#[derive(serde::Serialize)]
struct Error {
    code: String,
    message: String,
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
        return Err(Error {
            code: "non-existent-base-folder".into(),
            message: "The base folder doesn't exist.".into(),
        });
    }
    path.push(folder_name);
    match path.try_exists() {
        Ok(true) => Err(Error {
            code: "folder-already-exists".into(),
            message: "The folder already exists.".into(),
        }),
        Ok(false) => Ok(()),
        Err(e) => Err(Error {
            code: "file-system-error".into(),
            message: e.to_string(),
        }),
    }
}
