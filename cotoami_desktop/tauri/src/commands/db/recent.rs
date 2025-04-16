use std::{
    fs::{File, OpenOptions},
    io::{BufReader, BufWriter, Write},
    path::Path,
};

use anyhow::Result;
use base64::Engine;
use cotoami_db::prelude::Node;
use tracing::debug;

use crate::message::MessageSink;

#[derive(Debug, serde::Serialize, serde::Deserialize)]
pub(crate) struct DatabaseOpened {
    folder: String,
    name: String,
    icon: String, // Base64-encoded image binary
}

impl DatabaseOpened {
    fn new(folder: String, node: &Node) -> Self {
        Self {
            folder,
            name: node.name.clone(),
            icon: base64::engine::general_purpose::STANDARD_NO_PAD.encode(&node.icon),
        }
    }
}

#[derive(Debug, serde::Serialize, serde::Deserialize)]
#[serde(transparent)]
pub(crate) struct RecentDatabases(Vec<DatabaseOpened>);

impl RecentDatabases {
    const FILENAME: &'static str = ".recent.json";
    const MAX_SIZE: usize = 10;

    pub fn load(app_handle: &tauri::AppHandle) -> Self {
        if let Some(path) = crate::config_file_path(app_handle, Self::FILENAME) {
            debug!("Reading the recent file: {}", path.to_string_lossy());
            match Self::read_from_file(path) {
                Ok(recent) => recent,
                Err(e) => {
                    app_handle.error("Error reading the recent file.", Some(&e.to_string()));
                    Self::empty()
                }
            }
        } else {
            debug!("No recent databases.");
            Self::empty()
        }
    }

    pub fn update(app_handle: &tauri::AppHandle, folder: String, node: &Node) {
        let mut recent = Self::load(app_handle);
        recent.opened(DatabaseOpened::new(folder, node));
        recent.save(app_handle);
    }

    pub fn delete_invalid_folders(&mut self, app_handle: &tauri::AppHandle) {
        self.0
            .retain(|db| super::validate_database_folder(&db.folder).is_ok());
        self.save(app_handle);
    }

    fn empty() -> Self { RecentDatabases(Vec::new()) }

    fn read_from_file<P: AsRef<Path>>(path: P) -> Result<Self> {
        let file = File::open(path)?;
        let reader = BufReader::new(file);
        let recent = serde_json::from_reader(reader)?;
        Ok(recent)
    }

    fn opened(&mut self, db: DatabaseOpened) {
        self.0.retain(|x| x.folder != db.folder);
        self.0.insert(0, db);
        self.0.truncate(Self::MAX_SIZE)
    }

    fn save(&self, app_handle: &tauri::AppHandle) {
        if let Some(config_dir) = app_handle.path_resolver().app_config_dir() {
            let file_path = config_dir.join(Self::FILENAME);
            if let Err(e) = self.save_to_file(&file_path) {
                app_handle.error("Error writing the recent file.", Some(&e.to_string()));
            } else {
                debug!("RecentDatabases saved: {}", file_path.to_string_lossy());
            }
        }
    }

    fn save_to_file<P: AsRef<Path>>(&self, path: P) -> Result<()> {
        let file = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .open(path)?;
        let mut writer = BufWriter::new(file);
        serde_json::to_writer(&mut writer, self)?;
        writer.flush()?;
        Ok(())
    }
}
