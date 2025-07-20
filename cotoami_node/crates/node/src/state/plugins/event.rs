#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
pub enum PluginEvent {
    Registered {
        identifier: String,
        name: String,
        version: String,
    },
    InvalidFile {
        path: String,
        message: String,
    },
    Info {
        identifier: String,
        message: String,
    },
    Error {
        identifier: String,
        message: String,
    },
    Destroyed {
        identifier: String,
    },
}

impl PluginEvent {
    pub fn info(identifier: impl Into<String>, message: impl Into<String>) -> Self {
        PluginEvent::Info {
            identifier: identifier.into(),
            message: message.into(),
        }
    }

    pub fn error(identifier: impl Into<String>, message: impl Into<String>) -> Self {
        PluginEvent::Error {
            identifier: identifier.into(),
            message: message.into(),
        }
    }
}
