#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

use cotoami_desktop::{commands, plugins};
use log::LevelFilter;
use tauri_plugin_log::LogTarget;

fn main() {
    tauri::Builder::default()
        .plugin(plugins::window_state::Builder::default().build())
        .plugin(
            tauri_plugin_log::Builder::default()
                .targets([LogTarget::Stdout])
                .level(LevelFilter::Debug)
                .build(),
        )
        .manage(commands::OperatingAs::default())
        .invoke_handler(tauri::generate_handler![
            commands::node_command,
            commands::system::system_info,
            commands::db::validate_new_database_folder,
            commands::db::validate_database_folder,
            commands::db::create_database,
            commands::db::open_database,
            commands::conn::connect_to_servers
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
