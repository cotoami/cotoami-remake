use std::{
    collections::{BTreeMap, HashMap},
    fs::{self, DirEntry},
    path::{Path, PathBuf},
    str::FromStr,
};

use anyhow::{ensure, Result};
use cotoami_db::prelude::*;
use cotoami_plugin_api::*;
use extism::*;
use thiserror::Error;
use toml::{Table, Value};
use tracing::{debug, info};

pub struct Plugin(extism::Plugin);

impl Plugin {
    const FILE_NAME_SUFFIX: &'static str = ".wasm";

    fn is_plugin_file(entry: &DirEntry) -> bool {
        entry
            .file_name()
            .to_str()
            .map(|name| name.ends_with(Plugin::FILE_NAME_SUFFIX))
            .unwrap_or(false)
    }

    pub fn metadata(&mut self) -> Result<PluginMetadata> {
        self.0.call::<(), PluginMetadata>("metadata", ())
    }
}

#[derive(Debug, serde::Serialize, serde::Deserialize)]
#[serde(transparent)]
pub struct PluginConfig(Table);

impl PluginConfig {
    pub fn disabled(&self) -> bool {
        if let Some(Value::Boolean(disabled)) = self.0.get("disabled") {
            *disabled
        } else {
            false
        }
    }

    pub fn agent_node_id(&self) -> Option<Id<Node>> {
        if let Some(Value::String(id)) = self.0.get("agent_node_id") {
            Id::from_str(id).ok()
        } else {
            None
        }
    }
}

#[derive(Default)]
pub struct Plugins {
    metadata: Vec<PluginMetadata>,
    plugins: HashMap<String, Plugin>,
    configs: BTreeMap<String, PluginConfig>,
}

impl Plugins {
    const CONFIGS_FILE_NAME: &'static str = "configs.toml";

    pub fn load_from_dir<P: AsRef<Path>>(&mut self, plugins_dir: P) -> Result<()> {
        let path = plugins_dir.as_ref().canonicalize()?;
        info!("Loading plugins from: {path:?}");

        self.load_configs(plugins_dir)?;

        for entry in
            fs::read_dir(&path).map_err(|e| PluginError::InvalidPluginsDir(path, Some(e)))?
        {
            let entry = entry?;
            if Plugin::is_plugin_file(&entry) {
                let path = entry.path();
                debug!("Loading a plugin: {path:?}");
                let manifest = Manifest::new([Wasm::file(path)]);
                let plugin = Plugin(extism::Plugin::new(&manifest, [], true)?);
                self.register(plugin)?;
            }
        }
        Ok(())
    }

    fn load_configs<P: AsRef<Path>>(&mut self, plugins_dir: P) -> Result<()> {
        let path = plugins_dir.as_ref().join(Self::CONFIGS_FILE_NAME);
        if path.is_file() {
            info!("Plugins configs: {path:?}");
            let file_content = fs::read_to_string(path)?;
            self.configs = toml::from_str(&file_content)?;
        } else {
            debug!("No plugin configs: {path:?}");
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

#[derive(Error, Debug)]
pub enum PluginError {
    #[error("Invalid plugins dir path {0}: {1:?}")]
    InvalidPluginsDir(PathBuf, Option<std::io::Error>),

    #[error("Duplicate plugin: {0}")]
    DuplicatePlugin(String),
}
