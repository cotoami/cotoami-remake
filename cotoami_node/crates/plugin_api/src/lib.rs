use std::collections::{hash_map::Iter, HashMap};

use anyhow::{bail, Result};
use extism_pdk::*;
use serde_json::{json, Value};
use thiserror::Error;

mod event;
mod models;

pub use event::*;
pub use models::*;

pub const VERSION: &str = env!("CARGO_PKG_VERSION");

#[derive(Debug, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct Metadata {
    pub identifier: String,
    pub name: String,
    pub version: String,
    pub api_version: Option<String>,
    pub agent_name: Option<String>,
    pub agent_icon: Option<Vec<u8>>,
}

impl Metadata {
    pub fn new(
        identifier: impl Into<String>,
        name: impl Into<String>,
        version: impl Into<String>,
    ) -> Self {
        Self {
            identifier: identifier.into(),
            name: name.into(),
            version: version.into(),
            api_version: Some(VERSION.to_owned()),
            agent_name: None,
            agent_icon: None,
        }
    }

    pub fn mark_as_agent(mut self, name: impl Into<String>, icon: Vec<u8>) -> Self {
        self.agent_name = Some(name.into());
        self.agent_icon = Some(icon);
        self
    }

    pub fn as_agent(&self) -> Option<(&str, &[u8])> {
        match (&self.agent_name, &self.agent_icon) {
            (Some(name), Some(icon)) => Some((name, icon)),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Default, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[serde(transparent)]
#[encoding(Json)]
pub struct Config(HashMap<String, Value>);

impl Config {
    const KEY_DISABLED: &'static str = "disabled";
    const KEY_AGENT_NODE_ID: &'static str = "agent_node_id";
    const KEY_ALLOWED_HOSTS: &'static str = "allowed_hosts";
    const KEY_ALLOW_EDIT_USER_CONTENT: &'static str = "allow_edit_user_content";

    pub fn to_string(value: &Value) -> String {
        match value {
            Value::String(s) => s.clone(),
            other => other.to_string(),
        }
    }

    pub fn iter(&self) -> Iter<'_, String, Value> { self.0.iter() }

    pub fn require_key(&self, key: &str) -> Result<()> {
        if self.0.contains_key(key) {
            Ok(())
        } else {
            bail!(PluginError::MissingConfig(key.to_owned()));
        }
    }

    pub fn disabled(&self) -> bool {
        if let Some(Value::Bool(disabled)) = self.0.get(Self::KEY_DISABLED) {
            *disabled
        } else {
            false
        }
    }

    pub fn set_disabled(&mut self, disabled: bool) {
        self.0.insert(Self::KEY_DISABLED.into(), json!(disabled));
    }

    pub fn agent_node_id(&self) -> Option<&str> {
        if let Some(Value::String(id)) = self.0.get(Self::KEY_AGENT_NODE_ID) {
            Some(id)
        } else {
            None
        }
    }

    pub fn set_agent_node_id(&mut self, id: impl Into<String>) {
        self.0
            .insert(Self::KEY_AGENT_NODE_ID.into(), json!(id.into()));
    }

    pub fn allowed_hosts(&self) -> Vec<String> {
        match self.0.get(Self::KEY_ALLOWED_HOSTS) {
            Some(Value::String(host)) => vec![host.clone()],
            Some(Value::Array(hosts)) => hosts
                .into_iter()
                .map(|host| Self::to_string(host))
                .collect(),
            _ => Vec::new(),
        }
    }

    pub fn allow_edit_user_content(&self) -> bool {
        if let Some(Value::Bool(allow)) = self.0.get(Self::KEY_ALLOW_EDIT_USER_CONTENT) {
            *allow
        } else {
            false
        }
    }
}

#[derive(Error, Debug)]
pub enum PluginError {
    #[error("Required configuration key '{0}' is missing.")]
    MissingConfig(String),
}
