use std::{collections::BTreeMap, fs, path::Path};

use anyhow::Result;
use cotoami_db::{Id, Node};
use cotoami_node::prelude::NodeConfig;
use tauri::Manager;
use tracing::debug;

use crate::message::MessageSink;

#[derive(Debug, serde::Serialize, serde::Deserialize)]
#[serde(transparent)]
pub(crate) struct Configs(BTreeMap<Id<Node>, NodeConfig>);

impl Configs {
    const FILENAME: &'static str = "configs.toml";

    pub fn load(app_handle: &tauri::AppHandle) -> Self {
        if let Some(path) = crate::existing_config_file_path(app_handle, Self::FILENAME) {
            match Self::read_from_file(path) {
                Ok(configs) => configs,
                Err(e) => {
                    app_handle.error("Error reading the configs file.", Some(&e.to_string()));
                    Self::empty()
                }
            }
        } else {
            debug!("No configs file.");
            Self::empty()
        }
    }

    pub fn get_mut(&mut self, node_id: &Id<Node>) -> Option<&mut NodeConfig> {
        self.0.get_mut(node_id)
    }

    pub fn insert(&mut self, node_id: Id<Node>, config: NodeConfig) {
        self.0.insert(node_id, config);
    }

    pub fn save(&self, app_handle: &tauri::AppHandle) {
        match app_handle.path().app_config_dir() {
            Ok(config_dir) => {
                let file_path = config_dir.join(Self::FILENAME);
                if let Err(e) = self.save_to_file(&file_path) {
                    app_handle.error("Failed to write the configs file.", Some(e));
                } else {
                    app_handle.info("Configuration updated.", Some(file_path.to_string_lossy()));
                }
            }
            Err(e) => {
                app_handle.error("Failed to get a config dir.", Some(e));
            }
        }
    }

    fn empty() -> Self { Configs(BTreeMap::new()) }

    fn read_from_file<P: AsRef<Path>>(path: P) -> Result<Self> {
        toml::from_str(&fs::read_to_string(path)?).map_err(anyhow::Error::from)
    }

    fn save_to_file<P: AsRef<Path>>(&self, path: P) -> Result<()> {
        fs::write(path, toml::to_string(self)?).map_err(anyhow::Error::from)
    }
}

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use anyhow::Result;
    use googletest::prelude::*;
    use indoc::indoc;

    use super::*;

    #[test]
    fn serialize_to_toml() -> Result<()> {
        let mut configs = Configs::empty();
        configs.insert(
            Id::from_str("00000000-0000-0000-0000-000000000001")?,
            NodeConfig::new_standalone(Some("/path/to/db1".into()), Some("Hello".into())),
        );
        configs.insert(
            Id::from_str("00000000-0000-0000-0000-000000000002")?,
            NodeConfig::new_standalone(Some("/path/to/db2".into()), Some("Bye".into())),
        );
        assert_that!(
            toml::to_string(&configs).unwrap(),
            eq(indoc! {r#"
                [00000000-0000-0000-0000-000000000001]
                db_dir = "/path/to/db1"
                node_name = "Hello"
                session_minutes = 1440
                changes_chunk_size = 30
                max_message_size_as_client = 1073741824
                max_message_size_as_server = 67108864

                [00000000-0000-0000-0000-000000000002]
                db_dir = "/path/to/db2"
                node_name = "Bye"
                session_minutes = 1440
                changes_chunk_size = 30
                max_message_size_as_client = 1073741824
                max_message_size_as_server = 67108864
            "#})
        );
        Ok(())
    }
}
