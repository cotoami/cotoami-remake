use std::{
    collections::HashMap,
    fs::{self, DirEntry},
    path::{Path, PathBuf},
    sync::Arc,
};

use anyhow::{ensure, Result};
use cotoami_plugin_api::*;
use extism::*;
use parking_lot::Mutex;
use thiserror::Error;
use tracing::debug;

pub struct Plugin(Mutex<extism::Plugin>);

impl Plugin {
    pub fn new(plugin: extism::Plugin) -> Self { Self(Mutex::new(plugin)) }

    pub fn metadata(&self) -> Result<PluginMetadata> {
        self.0.lock().call::<(), PluginMetadata>("metadata", ())
    }
}

#[derive(Default)]
pub struct Plugins {
    plugins: HashMap<String, Plugin>,
    metadata: Vec<PluginMetadata>,
}

impl Plugins {
    pub fn load<P: AsRef<Path>>(plugins_dir: P) -> Result<Self> {
        let mut plugins = Self::default();
        let path = plugins_dir.as_ref().canonicalize()?;
        for entry in
            fs::read_dir(&path).map_err(|e| PluginError::InvalidPluginsDir(path, Some(e)))?
        {
            let entry = entry?;
            if check_if_plugin_file(&entry) {
                let path = entry.path();
                debug!("Loading a plugin: {path:?}");
                let manifest = Manifest::new([Wasm::file(path)]);
                let plugin = Plugin::new(extism::Plugin::new(&manifest, [], true)?);
                plugins.register(plugin)?;
            }
        }
        Ok(plugins)
    }

    fn register(&mut self, plugin: Plugin) -> Result<()> {
        let metadata = plugin.metadata()?;
        let identifier = metadata.identifier.clone();
        ensure!(
            !self.plugins.contains_key(&identifier),
            PluginError::DuplicatePlugin(identifier)
        );

        debug!("Registering a plugin: {metadata:?}");
        self.plugins.insert(identifier, plugin);
        self.metadata.push(metadata);
        Ok(())
    }
}

const PLUGIN_FILE_NAME_SUFFIX: &'static str = ".wasm";

fn check_if_plugin_file(entry: &DirEntry) -> bool {
    entry
        .file_name()
        .to_str()
        .map(|name| name.ends_with(PLUGIN_FILE_NAME_SUFFIX))
        .unwrap_or(false)
}

#[derive(Error, Debug)]
pub enum PluginError {
    #[error("Invalid plugins dir path {0}: {1:?}")]
    InvalidPluginsDir(PathBuf, Option<std::io::Error>),

    #[error("Duplicate plugin: {0}")]
    DuplicatePlugin(String),
}
