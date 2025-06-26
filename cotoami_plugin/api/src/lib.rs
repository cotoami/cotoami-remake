use std::collections::HashMap;

use extism_pdk::*;
use serde_json::Value;

#[derive(Debug, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct Metadata {
    pub identifier: String,
    pub name: String,
    pub version: String,
}

#[derive(Debug, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[serde(transparent)]
#[encoding(Json)]
pub struct Config(HashMap<String, Value>);

impl Config {
    pub fn disabled(&self) -> bool {
        if let Some(Value::Bool(disabled)) = self.0.get("disabled") {
            *disabled
        } else {
            false
        }
    }

    pub fn agent_node_id(&self) -> Option<&str> {
        if let Some(Value::String(id)) = self.0.get("agent_node_id") {
            Some(id)
        } else {
            None
        }
    }
}
