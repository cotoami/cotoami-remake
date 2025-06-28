use std::{
    collections::{BTreeMap, HashMap},
    fs,
    path::{Path, PathBuf},
    str::FromStr,
    sync::Arc,
};

use anyhow::{ensure, Result};
use cotoami_db::prelude::Id;
use cotoami_plugin_api::*;
use futures::StreamExt;
use parking_lot::Mutex;
use thiserror::Error;
use tokio::task::AbortHandle;
use tracing::{debug, error, info};

use self::plugin::Plugin;
use crate::state::NodeState;

mod convert;
mod plugin;

pub struct PluginSystem {
    plugins_dir: PathBuf,
    node_state: Option<NodeState>,
    plugins: Arc<Mutex<Plugins>>,
    event_loop: Option<AbortHandle>,
}

impl PluginSystem {
    pub fn new<P: AsRef<Path>>(plugins_dir: P) -> Result<Self> {
        let plugins_dir = plugins_dir.as_ref().canonicalize()?;
        ensure!(
            plugins_dir.is_dir(),
            PluginError::InvalidPluginsDir(plugins_dir, None)
        );
        let plugins = Arc::new(Mutex::new(Plugins::new(&plugins_dir)?));
        Ok(Self {
            plugins_dir: plugins_dir,
            node_state: None,
            plugins,
            event_loop: None,
        })
    }

    pub async fn init(&mut self, node_state: NodeState) -> Result<()> {
        self.node_state = Some(node_state);
        info!("Loading plugins from: {:?}", self.plugins_dir);
        for entry in fs::read_dir(&self.plugins_dir)? {
            let path = entry?.path();
            if !Plugin::is_plugin_file(&path) {
                continue;
            }
            match Plugin::new(&path) {
                Ok(plugin) => {
                    if let Err(e) = self.register(plugin).await {
                        error!("Couldn't register a plugin {path:?}: {e}");
                    }
                }
                Err(e) => {
                    error!("Invalid plugin {path:?}: {e}");
                }
            }
        }
        self.start_event_loop()?;
        Ok(())
    }

    async fn register(&mut self, plugin: Plugin) -> Result<()> {
        self.plugins.lock().ensure_unregistered(&plugin)?;
        self.register_agent(plugin.metadata()).await?;
        self.plugins.lock().register(plugin)?;
        Ok(())
    }

    async fn register_agent(&mut self, metadata: &Metadata) -> Result<()> {
        if let Some(node_state) = &self.node_state {
            // Already registered?
            if let Some(agent_node_id) = self.plugins.lock().agent_node_id(&metadata.identifier) {
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
                {
                    let mut plugins = self.plugins.lock();
                    plugins
                        .config_mut(metadata.identifier.clone())
                        .set_agent_node_id(node.uuid);
                    plugins.save_configs()?;
                }
                info!(
                    "{}: agent node registered: {}",
                    metadata.identifier, node.uuid
                );
            }
        }
        Ok(())
    }

    fn start_event_loop(&mut self) -> Result<()> {
        let state = self.node_state.as_ref().unwrap();
        let event_loop = tokio::spawn({
            let local_node_id = state.try_get_local_node_id()?;
            let mut changes = state.pubsub().changes().subscribe(None::<()>);
            let plugins = self.plugins.clone();
            async move {
                while let Some(log) = changes.next().await {
                    if let Some(event) = convert::into_plugin_event(log.change, local_node_id) {
                        for (plugin, config) in plugins.lock().iter_enabled() {
                            send_event_to_plugin(&event, plugin, config);
                        }
                    }
                }
            }
        });
        self.event_loop = Some(event_loop.abort_handle());
        Ok(())
    }

    pub fn destroy_all(&mut self) {
        if let Some(event_loop) = self.event_loop.as_ref() {
            event_loop.abort();
        }
        self.plugins.lock().destroy_all();
    }
}

#[derive(Error, Debug)]
pub enum PluginError {
    #[error("Invalid plugins dir path {0}: {1:?}")]
    InvalidPluginsDir(PathBuf, Option<std::io::Error>),

    #[error("Duplicate plugin: {0}")]
    DuplicatePlugin(String),
}

struct Plugins {
    configs_path: PathBuf,
    configs: BTreeMap<String, Config>,
    plugins: HashMap<String, Plugin>,
}

impl Plugins {
    const CONFIGS_FILE_NAME: &'static str = "configs.toml";

    fn new<P: AsRef<Path>>(plugins_dir: P) -> Result<Self> {
        let mut plugins = Self {
            configs_path: plugins_dir.as_ref().join(Self::CONFIGS_FILE_NAME),
            configs: BTreeMap::default(),
            plugins: HashMap::default(),
        };
        plugins.load_configs()?;
        Ok(plugins)
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

    fn save_configs(&self) -> Result<()> {
        let path = &self.configs_path;
        let file_content = toml::to_string(&self.configs)?;
        fs::write(path, file_content)?;
        Ok(())
    }

    fn config(&self, identifier: &str) -> Config {
        self.configs
            .get(identifier)
            .cloned()
            .unwrap_or(Config::default())
    }

    fn config_mut(&mut self, identifier: String) -> &mut Config {
        self.configs.entry(identifier).or_insert(Config::default())
    }

    fn agent_node_id(&self, identifier: &str) -> Option<&str> {
        self.configs.get(identifier).and_then(|c| c.agent_node_id())
    }

    fn ensure_unregistered(&self, plugin: &Plugin) -> Result<()> {
        let identifier = plugin.identifier();
        ensure!(
            !self.plugins.contains_key(identifier),
            PluginError::DuplicatePlugin(identifier.to_string())
        );
        Ok(())
    }

    fn register(&mut self, mut plugin: Plugin) -> Result<()> {
        self.ensure_unregistered(&plugin)?;

        let identifier = plugin.identifier().to_owned();
        let config = self.config(&identifier);

        if config.disabled() {
            info!("{identifier}: disabled.");
        } else {
            // init a plugin only if not disabled
            plugin.init(&config)?;
        }

        self.plugins.insert(identifier.clone(), plugin);
        info!("{identifier}: registered.");
        Ok(())
    }

    fn iter_enabled(&mut self) -> impl Iterator<Item = (&mut Plugin, Option<&Config>)> {
        self.plugins.values_mut().filter_map(|plugin| {
            let config = self.configs.get(plugin.identifier());
            if config.map(|c| c.disabled()).unwrap_or(false) {
                None
            } else {
                Some((plugin, config))
            }
        })
    }

    fn destroy_all(&mut self) {
        for (plugin, _) in self.iter_enabled() {
            match plugin.destroy() {
                Ok(_) => info!("{}: destroyed.", plugin.identifier()),
                Err(e) => error!("{}: destroying error : {e}", plugin.identifier()),
            }
        }
    }
}

fn send_event_to_plugin(event: &Event, plugin: &mut Plugin, config: Option<&Config>) {
    if let Some(agent_node_id) = config.and_then(|c| c.agent_node_id()) {
        // Filter events caused by the target plugin.
        match event {
            Event::CotoPosted { coto, .. } => {
                if coto.posted_by_id == agent_node_id {
                    return; // exclude self post
                }
            }
        }
    }
    if let Err(e) = plugin.on(&event) {
        error!("{}: event handling error: {e}", plugin.identifier());
    }
}
