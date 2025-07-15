#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
pub enum PluginEvent {
    Registered { identifier: String },
    Error { identifier: String, message: String },
}
