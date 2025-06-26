use std::collections::HashMap;

use extism_pdk::*;
use serde_json::{json, Value};

#[derive(Debug, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct Metadata {
    pub identifier: String,
    pub name: String,
    pub version: String,
}

#[derive(Debug, Clone, Default, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[serde(transparent)]
#[encoding(Json)]
pub struct Config(HashMap<String, Value>);

impl Config {
    const KEY_DISABLED: &'static str = "disabled";
    const KEY_AGENT_NODE_ID: &'static str = "agent_node_id";

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
