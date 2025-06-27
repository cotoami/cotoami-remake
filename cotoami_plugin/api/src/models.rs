use extism_pdk::*;

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
    pub media_content: Option<bytes::Bytes>,
    pub media_type: Option<String>,
    pub is_cotonoma: bool,
    pub longitude: Option<f64>,
    pub latitude: Option<f64>,
    pub datetime_start: Option<String>,
    pub datetime_end: Option<String>,
    pub repost_of_id: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(derive_more::Debug, Default, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct CotoInput {
    pub posted_in_id: Option<String>,
    pub posted_by_id: Option<String>,
    pub content: Option<String>,
    pub summary: Option<String>,
    #[debug(skip)]
    pub media_content: Option<bytes::Bytes>,
    pub media_type: Option<String>,
    pub longitude: Option<f64>,
    pub latitude: Option<f64>,
    pub datetime_start: Option<String>,
    pub datetime_end: Option<String>,
}

impl CotoInput {
    pub fn new(content: String) -> Self {
        let mut input = CotoInput::default();
        input.content = Some(content);
        input
    }
}
