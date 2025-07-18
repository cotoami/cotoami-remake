use std::{
    collections::HashMap,
    fs,
    path::{Path, PathBuf},
    str::FromStr,
    sync::Arc,
};

use anyhow::{ensure, Result};
use cotoami_db::Id;
use cotoami_plugin_api::*;
use futures::StreamExt;
use parking_lot::RwLock;
use semver::Version;
use thiserror::Error;
use tokio::task::{spawn_blocking, AbortHandle};
use tracing::{error, info};

use self::convert::*;
pub use self::{configs::Configs, event::*, plugin::Plugin};
use crate::state::{pubsub::EventPubsub, NodeState};

mod configs;
mod convert;
mod event;
mod host_fn;
mod plugin;

pub struct PluginSystem {
    plugins_dir: PathBuf,
    node_state: Option<NodeState>,
    plugins: Plugins,
    event_loop: Option<AbortHandle>,
}

impl PluginSystem {
    pub fn new<P: AsRef<Path>>(plugins_dir: P, event_pubsub: EventPubsub) -> Result<Self> {
        let plugins_dir = plugins_dir.as_ref().canonicalize()?;
        ensure!(
            plugins_dir.is_dir(),
            PluginError::InvalidPluginsDir(plugins_dir, None)
        );
        let plugins = Plugins::new(&plugins_dir, event_pubsub)?;
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
            match Plugin::new(&path, &self.plugins.configs, node_state.clone()) {
                Ok(plugin) => {
                    let identifier = plugin.identifier().to_owned();
                    if let Err(e) = self.register(plugin).await {
                        error!("Couldn't register a plugin {path:?}: {e}");
                        node_state.publish_plugin_event(PluginEvent::error(
                            identifier,
                            format!("Couldn't register a plugin {path:?}: {e}"),
                        ));
                    }
                }
                Err(e) => {
                    error!("Invalid plugin {path:?}: {e}");
                    node_state.publish_plugin_event(PluginEvent::InvalidFile {
                        path: path.to_string_lossy().into_owned(),
                        message: e.to_string(),
                    });
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
        let event_loop = tokio::spawn({
            let state = self.node_state.as_ref().unwrap().clone();
            let local_node_id = state.try_get_local_node_id()?;
            let mut changes = state.pubsub().changes().subscribe(None::<()>);
            let plugins = self.plugins.clone();
            async move {
                while let Some(log) = changes.next().await {
                    if let Some(event) =
                        into_plugin_event(log.change, local_node_id, state.clone()).await
                    {
                        let event = Arc::new(event);
                        plugins.for_each_enabled(|plugin, config| {
                            send_event_to_plugin(
                                event.clone(),
                                plugin,
                                &config,
                                state.pubsub().events().clone(),
                            );
                        });
                    }
                }
            }
        });
        self.event_loop = Some(event_loop.abort_handle());
        Ok(())
    }

    pub fn configs(&self) -> &Configs { &self.plugins.configs }

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

    #[error("The plugin requires {requirement}, but the runtime is v{runtime_version}")]
    UnsupportedApiVersion {
        requirement: String,
        runtime_version: String,
    },

    #[error("Missing agent config: {0}")]
    MissingAgentConfig(String),
}

#[derive(Clone)]
struct Plugins {
    plugins: Arc<RwLock<HashMap<String, Plugin>>>,
    configs: Configs,
    api_version: Version,
    event_pubsub: EventPubsub,
}

impl Plugins {
    fn new<P: AsRef<Path>>(plugins_dir: P, event_pubsub: EventPubsub) -> Result<Self> {
        Ok(Self {
            plugins: Default::default(),
            configs: Configs::new(plugins_dir)?,
            api_version: Version::parse(cotoami_plugin_api::VERSION).unwrap(),
            event_pubsub,
        })
    }

    fn ensure_unregistered(&self, plugin: &Plugin) -> Result<()> {
        let identifier = plugin.identifier();
        ensure!(
            !self.plugins.read().contains_key(identifier),
            PluginError::DuplicatePlugin(identifier.to_string())
        );
        Ok(())
    }

    fn register(&self, plugin: Plugin) -> Result<()> {
        self.ensure_unregistered(&plugin)?;

        let identifier = plugin.identifier().to_owned();
        let name = plugin.metadata().name.clone();
        let version = plugin.metadata().version.clone();
        let config = self.configs.config(&identifier);

        // Check plugin's API version requirement
        if let Some(version_req) = plugin.api_version_requirement()? {
            ensure!(
                version_req.matches(&self.api_version),
                PluginError::UnsupportedApiVersion {
                    requirement: version_req.to_string(),
                    runtime_version: self.api_version.to_string()
                }
            )
        }

        if config.disabled() {
            info!("{identifier}: disabled.");
        } else {
            // init a plugin only if not disabled
            plugin.init(&config)?;
        }

        self.plugins.write().insert(identifier.clone(), plugin);
        info!("{identifier}: registered.");
        self.publish_event(PluginEvent::Registered {
            identifier,
            name,
            version,
        });
        Ok(())
    }

    fn for_each_enabled<F>(&self, mut f: F)
    where
        F: FnMut(&Plugin, Config),
    {
        for plugin in self.plugins.read().values() {
            let identifier = plugin.identifier().to_owned();
            if !self.configs.disabled(&identifier) {
                f(plugin, self.configs.config(&identifier))
            }
        }
    }

    fn destroy_all(&self) {
        self.for_each_enabled(|plugin, _| match plugin.destroy() {
            Ok(_) => {
                info!("{}: destroyed.", plugin.identifier());
                self.publish_event(PluginEvent::Destroyed {
                    identifier: plugin.identifier().to_owned(),
                });
            }
            Err(e) => {
                error!("{}: destroying error : {e}", plugin.identifier());
                self.publish_event(PluginEvent::error(
                    plugin.identifier(),
                    format!("Destroying error : {e}"),
                ));
            }
        })
    }

    fn publish_event(&self, event: PluginEvent) { self.event_pubsub.publish(event.into(), None); }
}

fn send_event_to_plugin(
    event: Arc<Event>,
    plugin: &Plugin,
    config: &Config,
    event_pubsub: EventPubsub,
) {
    // Filter events.
    if let Some(agent_node_id) = config.agent_node_id() {
        match &*event {
            Event::CotoPosted { coto, .. } => {
                if coto.posted_by_id == agent_node_id {
                    return; // exclude self post
                }
            }
            _ => (),
        }
    }

    // Let a plugin handle the event on a thread where blocking is acceptable.
    spawn_blocking({
        let plugin = plugin.clone();
        move || {
            if let Err(e) = plugin.on(&event) {
                error!("{}: event handling error: {e}", plugin.identifier());
                event_pubsub.publish(
                    PluginEvent::error(plugin.identifier(), format!("Event handling error: {e}"))
                        .into(),
                    None,
                );
            }
        }
    });
}

impl NodeState {
    fn publish_plugin_event(&self, event: PluginEvent) {
        self.pubsub().events().publish(event.into(), None);
    }
}
