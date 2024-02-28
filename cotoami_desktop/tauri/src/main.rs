#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

use std::string::ToString;

pub mod window_state;

fn main() {
    tauri::Builder::default()
        .plugin(window_state::Builder::default().build())
        .invoke_handler(tauri::generate_handler![system_info, error_test])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

#[derive(serde::Serialize)]
struct SystemInfo {
    app_data_dir: Option<String>,
}

#[tauri::command]
fn system_info(app_handle: tauri::AppHandle) -> SystemInfo {
    let app_data_dir = app_handle
        .path_resolver()
        .app_data_dir()
        .and_then(|path| path.into_os_string().into_string().ok());
    SystemInfo { app_data_dir }
}

#[derive(serde::Serialize)]
struct Error {
    code: String,
    message: String,
}

#[tauri::command]
fn error_test() -> Result<String, Error> {
    Err(Error {
        code: "test".into(),
        message: "Hi, I'm an error!".into(),
    })
}
