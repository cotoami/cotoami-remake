#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

use cotoami_desktop::commands;
use log::LevelFilter;
#[allow(unused_imports)]
use tauri::Manager;
use tauri_plugin_log::{Target, TargetKind};
use tauri_plugin_window_state::StateFlags;

fn main() {
    #[allow(unused_variables)]
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_os::init())
        .plugin(tauri_plugin_process::init())
        .plugin(
            tauri_plugin_window_state::Builder::default()
                // https://github.com/tauri-apps/tauri/issues/1564#issuecomment-2642142824
                .with_state_flags(StateFlags::all() & !StateFlags::VISIBLE)
                .build(),
        )
        .plugin(
            tauri_plugin_log::Builder::new()
                .targets([Target::new(TargetKind::Stdout)])
                .level(LevelFilter::Info)
                .build(),
        )
        .setup(|app| {
            #[cfg(desktop)]
            app.handle()
                .plugin(tauri_plugin_updater::Builder::new().build())?;
            Ok(())
        })
        .on_window_event(|window, event| match event {
            // On macOS, do not quit the app when the window is closed;
            // keep it running in the background
            #[cfg(target_os = "macos")]
            tauri::WindowEvent::CloseRequested { api, .. } => {
                window.hide().unwrap();
                api.prevent_close();
            }
            _ => {}
        })
        .invoke_handler(tauri::generate_handler![
            commands::show_window,
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
        .build(tauri::generate_context!())
        .expect("error while running tauri application")
        .run(|app, event| match event {
            // Handle clicking on the dock icon on macOS.
            // https://github.com/tauri-apps/tauri/issues/3084#issuecomment-2938951372
            #[cfg(target_os = "macos")]
            tauri::RunEvent::Reopen { .. } => {
                if let Some(window) = app.get_webview_window("main") {
                    window.show().unwrap();
                }
            }
            _ => {}
        });
}
