#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

mod db;
mod error;
mod event;
mod log;
mod plugins;
mod system;

fn main() {
    tauri::Builder::default()
        .plugin(plugins::window_state::Builder::default().build())
        .invoke_handler(tauri::generate_handler![
            system::system_info,
            db::validate_new_database_folder,
            db::validate_database_folder,
            db::create_database,
            db::open_database,
            db::node_command
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
