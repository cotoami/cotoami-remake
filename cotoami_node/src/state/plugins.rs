use std::{
    collections::HashMap,
    fs::{self, DirEntry},
    path::{Path, PathBuf},
};

use anyhow::{ensure, Result};
use cotoami_plugin_api::*;
use extism::*;
use thiserror::Error;
use tracing::{debug, info};

pub struct Plugin(extism::Plugin);

impl Plugin {
    pub fn metadata(&mut self) -> Result<PluginMetadata> {
        self.0.call::<(), PluginMetadata>("metadata", ())
    }
}

#[derive(Default)]
pub struct Plugins {
    plugins: HashMap<String, Plugin>,
    metadata: Vec<PluginMetadata>,
}

impl Plugins {
    pub fn load_from_dir<P: AsRef<Path>>(&mut self, plugins_dir: P) -> Result<()> {
        let path = plugins_dir.as_ref().canonicalize()?;
        info!("Loading plugins from: {path:?}");
        for entry in
            fs::read_dir(&path).map_err(|e| PluginError::InvalidPluginsDir(path, Some(e)))?
        {
            let entry = entry?;
            if check_if_plugin_file(&entry) {
                let path = entry.path();
                debug!("Loading a plugin: {path:?}");
                let manifest = Manifest::new([Wasm::file(path)]);
                let plugin = Plugin(extism::Plugin::new(&manifest, [], true)?);
                self.register(plugin)?;
            }
        }
        Ok(())
    }

    fn register(&mut self, mut plugin: Plugin) -> Result<()> {
        let metadata = plugin.metadata()?;
        let identifier = metadata.identifier.clone();
        ensure!(
            !self.plugins.contains_key(&identifier),
            PluginError::DuplicatePlugin(identifier)
        );

        self.plugins.insert(identifier.clone(), plugin);
        self.metadata.push(metadata);
        info!("Registered a plugin: {identifier}");
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
