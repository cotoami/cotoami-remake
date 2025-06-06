#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

use cotoami_desktop::commands;
use log::LevelFilter;
use tauri_plugin_log::{Target, TargetKind};

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_window_state::Builder::default().build())
        .plugin(
            tauri_plugin_log::Builder::new()
                .targets([Target::new(TargetKind::Stdout)])
                .level(LevelFilter::Debug)
                .build(),
        )
        .invoke_handler(tauri::generate_handler![
            commands::node_command,
            commands::operate_as,
            commands::system::system_info,
            commands::db::validate_new_database_folder,
            commands::db::validate_database_folder,
            commands::db::create_database,
            commands::db::open_database,
            commands::db::new_owner_password,
            commands::conn::connect_to_servers
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
