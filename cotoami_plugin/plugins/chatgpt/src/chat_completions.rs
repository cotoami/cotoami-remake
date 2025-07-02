use extism_pdk::*;

#[derive(Debug, serde::Serialize, ToBytes)]
#[encoding(Json)]
pub struct RequestBody {
    pub model: String,
    pub messages: Vec<InputMessage>,
}

#[derive(Debug, serde::Serialize, ToBytes)]
#[encoding(Json)]
pub struct InputMessage {
    pub role: String,
    pub content: String,
    pub name: Option<String>,
}

#[derive(Debug, serde::Deserialize, FromBytes)]
#[encoding(Json)]
pub struct ResponseBody {
    pub id: String,
    pub object: String,
    pub created: i64,
    pub model: String,
    pub choices: Vec<Choice>,
}

#[derive(Debug, serde::Deserialize, FromBytes)]
#[encoding(Json)]
pub struct Choice {
    pub index: usize,
    pub message: Message,
}

#[derive(Debug, serde::Deserialize, FromBytes)]
#[encoding(Json)]
pub struct Message {
    pub role: String,
    pub content: String,
}
