use std::collections::HashMap;

use extism_pdk::*;

/////////////////////////////////////////////////////////////////////////////
// Node
/////////////////////////////////////////////////////////////////////////////

#[derive(derive_more::Debug, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct Node {
    pub uuid: String,
    pub name: String,
}

/////////////////////////////////////////////////////////////////////////////
// Coto
/////////////////////////////////////////////////////////////////////////////

#[derive(derive_more::Debug, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct Coto {
    pub uuid: String,
    pub node_id: String,
    pub posted_in_id: String,
    pub posted_by_id: String,
    pub content: Option<String>,
    pub summary: Option<String>,
    #[debug(skip)]
    pub media_content: Option<(bytes::Bytes, String)>,
    pub is_cotonoma: bool,
    pub geolocation: Option<Geolocation>,
    pub datetime_start: Option<String>,
    pub datetime_end: Option<String>,
    pub repost_of_id: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(derive_more::Debug, Default, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct Geolocation {
    pub longitude: f64,
    pub latitude: f64,
}

#[derive(derive_more::Debug, Default, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct CotoInput {
    pub content: String,
    pub summary: Option<String>,
    #[debug(skip)]
    pub media_content: Option<(bytes::Bytes, String)>,
    pub geolocation: Option<Geolocation>,
}

impl CotoInput {
    pub fn new(content: impl Into<String>) -> Self {
        Self {
            content: content.into(),
            ..Default::default()
        }
    }
}

#[derive(derive_more::Debug, Default, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct CotoContentDiff {
    pub content: Option<String>,
    pub summary: Option<String>,
    #[debug(skip)]
    pub media_content: Option<(bytes::Bytes, String)>,
    pub geolocation: Option<Geolocation>,
}

/////////////////////////////////////////////////////////////////////////////
// Ito
/////////////////////////////////////////////////////////////////////////////

#[derive(derive_more::Debug, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct Ito {
    pub uuid: String,
    pub node_id: String,
    pub created_by_id: String,
    pub source_coto_id: String,
    pub target_coto_id: String,
    pub description: Option<String>,
    pub order: i32,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(derive_more::Debug, Default, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct ItoInput {
    pub source_coto_id: String,
    pub target_coto_id: String,
    pub description: Option<String>,
}

impl ItoInput {
    pub fn new(source_coto_id: String, target_coto_id: String) -> Self {
        Self {
            source_coto_id,
            target_coto_id,
            ..Default::default()
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Graph
/////////////////////////////////////////////////////////////////////////////

#[derive(derive_more::Debug, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct Ancestors {
    pub ancestors: Vec<(Vec<Ito>, Vec<Coto>)>,
    pub authors: HashMap<String, Node>,
}
