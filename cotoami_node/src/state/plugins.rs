use std::{
    collections::{BTreeMap, HashMap},
    fs::{self, DirEntry},
    path::{Path, PathBuf},
    str::FromStr,
};

use anyhow::{ensure, Result};
use cotoami_db::prelude::Id;
use cotoami_plugin_api::*;
use extism::*;
use thiserror::Error;
use tracing::{debug, error, info};

use crate::state::NodeState;

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
    node_state: Option<NodeState>,
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
            node_state: None,
            metadata: Vec::default(),
            plugins: HashMap::default(),
            configs: BTreeMap::default(),
        })
    }

    pub async fn init(&mut self, node_state: NodeState) -> Result<()> {
        self.node_state = Some(node_state);
        info!("Loading plugins from: {:?}", self.plugins_dir);
        self.load_configs()?;
        for entry in fs::read_dir(&self.plugins_dir)? {
            let entry = entry?;
            if Plugin::is_plugin_file(&entry) {
                let path = entry.path();
                debug!("Loading a plugin: {path:?}");
                let manifest = Manifest::new([Wasm::file(&path)]);
                let plugin = Plugin(extism::Plugin::new(&manifest, [], true)?);
                if let Err(e) = self.register(plugin).await {
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

    async fn register(&mut self, mut plugin: Plugin) -> Result<()> {
        let metadata = plugin.metadata()?;
        let identifier = metadata.identifier.clone();

        ensure!(
            !self.plugins.contains_key(&identifier),
            PluginError::DuplicatePlugin(identifier)
        );

        self.register_agent(&metadata).await?;

        let config = self
            .configs
            .get(&identifier)
            .cloned()
            .unwrap_or(Config::default());
        if config.disabled() {
            info!("{identifier}: disabled.");
        } else {
            // init a plugin only if not disabled
            plugin.init(config)?;
        }

        self.plugins.insert(identifier.clone(), plugin);
        self.metadata.push(metadata);
        info!("{identifier}: registered.");
        Ok(())
    }

    fn config_mut(&mut self, metadata: &Metadata) -> &mut Config {
        self.configs
            .entry(metadata.identifier.clone())
            .or_insert(Config::default())
    }

    async fn register_agent(&mut self, metadata: &Metadata) -> Result<()> {
        if let Some(node_state) = &self.node_state {
            // Already registered?
            if let Some(agent_node_id) = self
                .configs
                .get(&metadata.identifier)
                .and_then(|c| c.agent_node_id())
            {
                if node_state
                    .node(Id::from_str(agent_node_id)?)
                    .await?
                    .is_some()
                {
                    info!(
                        "{}: agent node found: {}",
                        metadata.identifier, agent_node_id
                    );
                    return Ok(());
                }
            }

            // Register an agent node.
            if let Some((name, icon)) = metadata.as_agent() {
                let node = node_state
                    .clone()
                    .create_agent_node(name.into(), Vec::from(icon))
                    .await?;
                self.config_mut(&metadata).set_agent_node_id(node.uuid);
                self.save_configs()?;
                info!(
                    "{}: agent node registered: {}",
                    metadata.identifier, node.uuid
                );
            }
        }
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
