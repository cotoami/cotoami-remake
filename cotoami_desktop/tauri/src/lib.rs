use std::path::PathBuf;

use rand::{distributions::Alphanumeric, thread_rng, Rng};

use crate::log::Logger;

mod config;
pub mod db;
pub mod error;
mod event;
mod log;
pub mod plugins;
pub mod system;

fn config_file_path(app_handle: &tauri::AppHandle, file_name: &str) -> Option<PathBuf> {
    if let Some(config_dir) = app_handle.path_resolver().app_config_dir() {
        let file_path = config_dir.join(file_name);
        if file_path.is_file() {
            Some(file_path)
        } else {
            None
        }
    } else {
        app_handle.warn("app_config_dir is None.", None);
        None
    }
}

fn generate_password() -> String {
    // https://rust-lang-nursery.github.io/rust-cookbook/algorithms/randomness.html#create-random-passwords-from-a-set-of-alphanumeric-characters
    thread_rng()
        .sample_iter(&Alphanumeric)
        .take(32)
        .map(char::from)
        .collect()
}
