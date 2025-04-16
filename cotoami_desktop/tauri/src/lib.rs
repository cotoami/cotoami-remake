use std::path::PathBuf;

use tracing::warn;

pub mod commands;
mod config;
mod event;
mod message;
pub mod plugins;

fn config_file_path(app_handle: &tauri::AppHandle, file_name: &str) -> Option<PathBuf> {
    if let Some(config_dir) = app_handle.path_resolver().app_config_dir() {
        let file_path = config_dir.join(file_name);
        if file_path.is_file() {
            Some(file_path)
        } else {
            None
        }
    } else {
        warn!("app_config_dir is None.");
        None
    }
}
