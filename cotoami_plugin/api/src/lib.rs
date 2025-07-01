use std::collections::{hash_map::Iter, HashMap};

use extism_pdk::*;
use serde_json::{json, Value};

mod event;
mod models;

pub use event::*;
pub use models::*;

#[derive(Debug, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct Metadata {
    pub identifier: String,
    pub name: String,
    pub version: String,
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
    const KEY_ALLOWED_HOSTS: &'static str = "allowed_hosts";
    const KEY_DISABLED: &'static str = "disabled";
    const KEY_AGENT_NODE_ID: &'static str = "agent_node_id";

    pub fn iter(&self) -> Iter<'_, String, Value> { self.0.iter() }

    pub fn allowed_hosts(&self) -> Vec<String> {
        match self.0.get(Self::KEY_ALLOWED_HOSTS) {
            Some(Value::String(host)) => vec![host.clone()],
            Some(Value::Array(hosts)) => hosts.into_iter().map(|host| host.to_string()).collect(),
            _ => Vec::new(),
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
}
