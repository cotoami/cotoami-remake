use std::{
    collections::HashMap,
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
use tracing::{error, info};

use self::{configs::Configs, plugin::Plugin};
use crate::state::NodeState;

mod configs;
mod convert;
mod plugin;

pub struct PluginSystem {
    plugins_dir: PathBuf,
    node_state: Option<NodeState>,
    plugins: Plugins,
    event_loop: Option<AbortHandle>,
}

impl PluginSystem {
    pub fn new<P: AsRef<Path>>(plugins_dir: P) -> Result<Self> {
        let plugins_dir = plugins_dir.as_ref().canonicalize()?;
        ensure!(
            plugins_dir.is_dir(),
            PluginError::InvalidPluginsDir(plugins_dir, None)
        );
        let plugins = Plugins::new(&plugins_dir)?;
        Ok(Self {
            plugins_dir: plugins_dir,
            node_state: None,
            plugins,
            event_loop: None,
        })
    }

    pub async fn init(&mut self, node_state: NodeState) -> Result<()> {
        self.node_state = Some(node_state.clone());
        info!("Loading plugins from: {:?}", self.plugins_dir);
        for entry in fs::read_dir(&self.plugins_dir)? {
            let path = entry?.path();
            if !Plugin::is_plugin_file(&path) {
                continue;
            }
            match Plugin::new(&path, node_state.clone()) {
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

    async fn register(&self, plugin: Plugin) -> Result<()> {
        self.plugins.ensure_unregistered(&plugin)?;
        self.register_agent(plugin.metadata()).await?;
        self.plugins.register(plugin)?;
        Ok(())
    }

    async fn register_agent(&self, metadata: &Metadata) -> Result<()> {
        if let Some(node_state) = &self.node_state {
            // Already registered?
            if let Some(agent_node_id) = self.plugins.configs.agent_node_id(&metadata.identifier) {
                if node_state
                    .node(Id::from_str(&agent_node_id)?)
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
                let agent_node = node_state
                    .clone()
                    .create_agent_node(name.into(), Vec::from(icon))
                    .await?;

                self.plugins
                    .configs
                    .write(metadata.identifier.clone())
                    .set_agent_node_id(agent_node.uuid);
                self.plugins.configs.save()?;

                info!(
                    "{}: agent node registered: {}",
                    metadata.identifier, agent_node.uuid
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
                        plugins.for_each_enabled(|plugin, config| {
                            send_event_to_plugin(&event, plugin, &config);
                        });
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
            self.event_loop = None;
        }
        self.plugins.destroy_all();
    }
}

#[derive(Error, Debug)]
pub enum PluginError {
    #[error("Invalid plugins dir path {0}: {1:?}")]
    InvalidPluginsDir(PathBuf, Option<std::io::Error>),

    #[error("Duplicate plugin: {0}")]
    DuplicatePlugin(String),
}

#[derive(Clone)]
struct Plugins {
    plugins: Arc<Mutex<HashMap<String, Plugin>>>,
    configs: Configs,
}

impl Plugins {
    fn new<P: AsRef<Path>>(plugins_dir: P) -> Result<Self> {
        Ok(Self {
            plugins: Default::default(),
            configs: Configs::new(plugins_dir)?,
        })
    }

    fn ensure_unregistered(&self, plugin: &Plugin) -> Result<()> {
        let identifier = plugin.identifier();
        ensure!(
            !self.plugins.lock().contains_key(identifier),
            PluginError::DuplicatePlugin(identifier.to_string())
        );
        Ok(())
    }

    fn register(&self, mut plugin: Plugin) -> Result<()> {
        self.ensure_unregistered(&plugin)?;

        let identifier = plugin.identifier().to_owned();
        let config = self.configs.config(&identifier);

        if config.disabled() {
            info!("{identifier}: disabled.");
        } else {
            // init a plugin only if not disabled
            plugin.init(&config)?;
        }

        self.plugins.lock().insert(identifier.clone(), plugin);
        info!("{identifier}: registered.");
        Ok(())
    }

    fn for_each_enabled<F>(&self, mut f: F)
    where
        F: FnMut(&mut Plugin, Config),
    {
        for plugin in self.plugins.lock().values_mut() {
            let identifier = plugin.identifier().to_owned();
            if !self.configs.disabled(&identifier) {
                f(plugin, self.configs.config(&identifier))
            }
        }
    }

    fn destroy_all(&self) {
        self.for_each_enabled(|plugin, _| match plugin.destroy() {
            Ok(_) => info!("{}: destroyed.", plugin.identifier()),
            Err(e) => error!("{}: destroying error : {e}", plugin.identifier()),
        })
    }
}

fn send_event_to_plugin(event: &Event, plugin: &mut Plugin, config: &Config) {
    if let Some(agent_node_id) = config.agent_node_id() {
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
