use std::{
    collections::BTreeMap,
    fs,
    path::{Path, PathBuf},
    sync::Arc,
};

use anyhow::Result;
use cotoami_plugin_api::*;
use parking_lot::{
    MappedRwLockReadGuard, MappedRwLockWriteGuard, RwLock, RwLockReadGuard, RwLockWriteGuard,
};
use tracing::{debug, info};

pub(crate) struct Configs {
    configs_path: PathBuf,
    entries: Arc<RwLock<BTreeMap<String, Config>>>,
}

impl Configs {
    const CONFIGS_FILE_NAME: &'static str = "configs.toml";

    pub fn new<P: AsRef<Path>>(plugins_dir: P) -> Result<Self> {
        let configs = Self {
            configs_path: plugins_dir.as_ref().join(Self::CONFIGS_FILE_NAME),
            entries: Default::default(),
        };
        configs.load()?;
        Ok(configs)
    }

    fn load(&self) -> Result<()> {
        let path = &self.configs_path;
        if path.is_file() {
            info!("Plugins configs: {path:?}");
            let file_content = fs::read_to_string(path)?;
            *self.entries.write() = toml::from_str(&file_content)?;
        } else {
            debug!("No plugin configs: {path:?}");
        }
        Ok(())
    }

    pub fn save(&self) -> Result<()> {
        let path = &self.configs_path;
        let file_content = toml::to_string(&*self.entries.read())?;
        fs::write(path, file_content)?;
        Ok(())
    }

    pub fn agent_node_id(&self, identifier: &str) -> Option<String> {
        self.read(identifier)
            .and_then(|config| config.agent_node_id().map(ToOwned::to_owned))
    }

    pub fn config(&self, identifier: &str) -> Config {
        self.entries
            .read()
            .get(identifier)
            .cloned()
            .unwrap_or(Config::default())
    }

    pub fn read(&self, identifier: &str) -> Option<MappedRwLockReadGuard<Config>> {
        RwLockReadGuard::try_map(self.entries.read(), |entries| entries.get(identifier)).ok()
    }

    pub fn write(&self, identifier: String) -> MappedRwLockWriteGuard<Config> {
        RwLockWriteGuard::map(self.entries.write(), |entries| {
            entries.entry(identifier).or_insert(Config::default())
        })
    }
}
