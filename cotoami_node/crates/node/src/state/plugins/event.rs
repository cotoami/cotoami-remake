#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
pub enum PluginEvent {
    Registered { identifier: String, version: String },
    InvalidFile { path: String, message: String },
    Error { identifier: String, message: String },
    Destroyed { identifier: String },
}

impl PluginEvent {
    pub fn error(identifier: impl Into<String>, message: impl Into<String>) -> Self {
        PluginEvent::Error {
            identifier: identifier.into(),
            message: message.into(),
        }
    }
}
