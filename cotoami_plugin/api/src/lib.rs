use extism_pdk::*;

#[derive(Debug, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct PluginMetadata {
    pub identifier: String,
    pub name: String,
    pub version: String,
}
