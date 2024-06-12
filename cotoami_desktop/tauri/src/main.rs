#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

mod db;
mod error;
mod event;
mod log;
mod recent;
mod system;
mod window_state;

fn main() {
    tauri::Builder::default()
        .plugin(window_state::Builder::default().build())
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
