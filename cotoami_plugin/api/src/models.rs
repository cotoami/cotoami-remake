use chrono::NaiveDateTime;
use extism_pdk::*;

#[derive(Debug, serde::Serialize, serde::Deserialize, ToBytes, FromBytes)]
#[encoding(Json)]
pub struct Coto {
    pub uuid: String,
    pub node_id: String,
    pub posted_in_id: String,
    pub posted_by_id: String,
    pub content: Option<String>,
    pub summary: Option<String>,
    pub media_content: Option<bytes::Bytes>,
    pub media_type: Option<String>,
    pub is_cotonoma: bool,
    pub longitude: Option<f64>,
    pub latitude: Option<f64>,
    pub datetime_start: Option<NaiveDateTime>,
    pub datetime_end: Option<NaiveDateTime>,
    pub repost_of_id: Option<String>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
}
