use std::{
    collections::{BTreeMap, HashMap},
    fs::{self, DirEntry},
    path::{Path, PathBuf},
};

use anyhow::{ensure, Result};
use cotoami_plugin_api::*;
use extism::*;
use thiserror::Error;
use tracing::{debug, error, info};

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

    pub fn metadata(&mut self) -> Result<Metadata> { self.0.call::<(), Metadata>("metadata", ()) }

    pub fn init(&mut self, config: Config) -> Result<()> {
        self.0.call::<Config, ()>("init", config)
    }
}

pub struct Plugins {
    plugins_dir: PathBuf,
    configs_path: PathBuf,
    metadata: Vec<Metadata>,
    plugins: HashMap<String, Plugin>,
    configs: BTreeMap<String, Config>,
}

impl Plugins {
    const CONFIGS_FILE_NAME: &'static str = "configs.toml";

    pub fn new<P: AsRef<Path>>(plugins_dir: P) -> Result<Self> {
        let plugins_dir = plugins_dir.as_ref().canonicalize()?;
        ensure!(
            plugins_dir.is_dir(),
            PluginError::InvalidPluginsDir(plugins_dir, None)
        );
        let configs_path = plugins_dir.join(Self::CONFIGS_FILE_NAME);
        Ok(Self {
            plugins_dir,
            configs_path,
            metadata: Vec::default(),
            plugins: HashMap::default(),
            configs: BTreeMap::default(),
        })
    }

    pub fn init(&mut self) -> Result<()> {
        info!("Loading plugins from: {:?}", self.plugins_dir);
        self.load_configs()?;
        for entry in fs::read_dir(&self.plugins_dir)? {
            let entry = entry?;
            if Plugin::is_plugin_file(&entry) {
                let path = entry.path();
                debug!("Loading a plugin: {path:?}");
                let manifest = Manifest::new([Wasm::file(&path)]);
                let plugin = Plugin(extism::Plugin::new(&manifest, [], true)?);
                if let Err(e) = self.register(plugin) {
                    error!("Couldn't register a plugin {path:?}: {e}");
                }
            }
        }
        Ok(())
    }

    fn load_configs(&mut self) -> Result<()> {
        let path = &self.configs_path;
        if path.is_file() {
            info!("Plugins configs: {path:?}");
            let file_content = fs::read_to_string(path)?;
            self.configs = toml::from_str(&file_content)?;
        } else {
            debug!("No plugin configs: {path:?}");
        }
        Ok(())
    }

    pub fn save_configs(&self) -> Result<()> {
        let path = &self.configs_path;
        let file_content = toml::to_string(&self.configs)?;
        fs::write(path, file_content)?;
        Ok(())
    }

    fn register(&mut self, mut plugin: Plugin) -> Result<()> {
        let metadata = plugin.metadata()?;
        let identifier = metadata.identifier.clone();
        ensure!(
            !self.plugins.contains_key(&identifier),
            PluginError::DuplicatePlugin(identifier)
        );

        plugin.init(self.config(&identifier))?;

        self.plugins.insert(identifier.clone(), plugin);
        self.metadata.push(metadata);
        info!("Registered a plugin: {identifier}");
        Ok(())
    }

    fn config(&self, identifier: &str) -> Config {
        self.configs
            .get(identifier)
            .cloned()
            .unwrap_or(Config::default())
    }
}

#[derive(Error, Debug)]
pub enum PluginError {
    #[error("Invalid plugins dir path {0}: {1:?}")]
    InvalidPluginsDir(PathBuf, Option<std::io::Error>),

    #[error("Duplicate plugin: {0}")]
    DuplicatePlugin(String),
}
