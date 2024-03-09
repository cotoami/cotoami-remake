use std::{
    fs::{File, OpenOptions},
    io::{BufReader, BufWriter, Write},
    path::Path,
};

use anyhow::Result;
use base64::Engine;
use cotoami_db::prelude::Node;

use crate::log::Logger;

#[derive(Debug, serde::Serialize, serde::Deserialize)]
pub(crate) struct DatabaseFolder {
    path: String,
    name: String,
    icon: String, // Base64-encoded image binary
}

impl DatabaseFolder {
    fn new(path: String, node: &Node) -> Self {
        Self {
            path,
            name: node.name.clone(),
            icon: base64::engine::general_purpose::STANDARD_NO_PAD.encode(&node.icon),
        }
    }
}

#[derive(Debug, serde::Serialize, serde::Deserialize)]
#[serde(transparent)]
pub(crate) struct RecentDatabases(Vec<DatabaseFolder>);

impl RecentDatabases {
    const FILENAME: &'static str = ".recent.json";
    const MAX_SIZE: usize = 10;

    pub fn load(app_handle: &tauri::AppHandle) -> Self {
        if let Some(config_dir) = app_handle.path_resolver().app_config_dir() {
            let file_path = config_dir.join(Self::FILENAME);
            if file_path.is_file() {
                app_handle.debug(
                    "Reading the recent file...",
                    Some(file_path.to_string_lossy().to_string()),
                );
                match Self::read_from_file(file_path) {
                    Ok(recent) => recent,
                    Err(e) => {
                        app_handle.warn("Error reading the recent file.", Some(e.to_string()));
                        Self::empty()
                    }
                }
            } else {
                app_handle.debug("No recent databases.", None);
                Self::empty()
            }
        } else {
            Self::empty()
        }
    }

    fn empty() -> Self { RecentDatabases(Vec::new()) }

    fn read_from_file<P: AsRef<Path>>(path: P) -> Result<Self> {
        let file = File::open(path)?;
        let reader = BufReader::new(file);
        let recent = serde_json::from_reader(reader)?;
        Ok(recent)
    }

    pub fn update(app_handle: &tauri::AppHandle, path: String, node: &Node) {
        let mut recent = Self::load(app_handle);
        recent.opened(DatabaseFolder::new(path, node));
        recent.save(app_handle);
    }

    fn opened(&mut self, folder: DatabaseFolder) {
        self.0.retain(|f| f.path != folder.path);
        self.0.insert(0, folder);
        self.0.truncate(Self::MAX_SIZE)
    }

    fn save(&self, app_handle: &tauri::AppHandle) {
        if let Some(config_dir) = app_handle.path_resolver().app_config_dir() {
            let file_path = config_dir.join(Self::FILENAME);
            if let Err(e) = self.save_to_file(&file_path) {
                app_handle.warn("Error writing the recent file.", Some(e.to_string()));
            } else {
                app_handle.debug(
                    "RecentDatabases saved.",
                    Some(file_path.to_string_lossy().to_string()),
                );
            }
        } else {
            app_handle.debug("app_config_dir is None.", None);
        }
    }

    fn save_to_file<P: AsRef<Path>>(&self, path: P) -> Result<()> {
        let file = OpenOptions::new().write(true).create(true).open(path)?;
        let mut writer = BufWriter::new(file);
        serde_json::to_writer(&mut writer, self)?;
        writer.flush()?;
        Ok(())
    }
}
