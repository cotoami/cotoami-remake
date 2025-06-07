use std::path::PathBuf;

use tauri::Manager;

use crate::message::MessageSink;

pub mod commands;
mod config;
mod event;
mod message;

fn existing_config_file_path(app_handle: &tauri::AppHandle, file_name: &str) -> Option<PathBuf> {
    match app_handle.path().app_config_dir() {
        Ok(config_dir) => {
            let file_path = config_dir.join(file_name);
            if file_path.is_file() {
                Some(file_path)
            } else {
                None
            }
        }
        Err(e) => {
            app_handle.error("Failed to get a config dir.", Some(e));
            None
        }
    }
}
