use extism_pdk::*;

use crate::models::*;

#[derive(Debug, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[serde(tag = "type")]
#[encoding(Json)]
pub enum Event {
    CotoPosted { coto: Coto, local_node_id: String },
}
