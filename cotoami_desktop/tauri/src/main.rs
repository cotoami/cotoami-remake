#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

use cotoami_desktop::{conn, db, plugins, system};
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
        .invoke_handler(tauri::generate_handler![
            system::system_info,
            db::validate_new_database_folder,
            db::validate_database_folder,
            db::create_database,
            db::open_database,
            db::node_command,
            conn::connect_to_servers
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
