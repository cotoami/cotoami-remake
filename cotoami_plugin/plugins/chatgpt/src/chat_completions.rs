use extism_pdk::*;

pub const ENDPOINT: &'static str = "https://api.openai.com/v1/chat/completions";

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

impl InputMessage {
    pub fn by_user(content: String, name: String) -> Self {
        InputMessage {
            role: "user".into(),
            content,
            name: Some(name),
        }
    }

    pub fn by_assistant(content: String) -> Self {
        InputMessage {
            role: "assistant".into(),
            content,
            name: None,
        }
    }
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
