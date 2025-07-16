//! Chat Completions
//! https://platform.openai.com/docs/api-reference/chat

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
    const SPEAKER_TAG_INSTRUCTION: &'static str = 
        "`[User: Name]` is used for speaker tags. No need to add any prefix like `[Assistant]` in your responses.";
    const SUMMARY_INSTRUCTION: &'static str =
        "Detect the language of the following text and summarize its content in that language using 150 characters or fewer. \n\n";

    pub fn speaker_tag_instruction() -> Self {
        Self::by_developer(Self::SPEAKER_TAG_INSTRUCTION.into())
    }

    pub fn summary_request(content: &str) -> Self {
        InputMessage {
            role: "user".into(),
            content: format!("{}{}", Self::SUMMARY_INSTRUCTION, content),
            name: None,
        }
    }

    pub fn by_developer(message: String) -> Self {
        InputMessage {
            role: "developer".into(),
            content: message,
            name: None,
        }
    }

    pub fn by_user(message: String, id: String, name: Option<String>) -> Self {
        let content = if let Some(name) = name {
            // Embed the user name in the content to help ChatGPT recognize the speaker.
            format!("[User: {name}] {message}")
        } else {
            message
        };
        InputMessage {
            role: "user".into(),
            content,
            name: Some(id),
        }
    }

    pub fn by_assistant(message: String) -> Self {
        InputMessage {
            role: "assistant".into(),
            content: message,
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
