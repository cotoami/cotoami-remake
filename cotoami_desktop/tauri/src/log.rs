use log::error;
use tauri::{AppHandle, Manager};

pub(crate) trait Logger {
    fn log(&self, entry: &LogEntry);

    fn debug(&self, message: impl Into<String>, details: Option<String>) {
        self.log(&LogEntry::new(LogEntry::LEVEL_DEBUG, message, details));
    }
    fn info(&self, message: impl Into<String>, details: Option<String>) {
        self.log(&LogEntry::new(LogEntry::LEVEL_INFO, message, details));
    }
    fn warn(&self, message: impl Into<String>, details: Option<String>) {
        self.log(&LogEntry::new(LogEntry::LEVEL_WARN, message, details));
    }
    fn error(&self, message: impl Into<String>, details: Option<String>) {
        self.log(&LogEntry::new(LogEntry::LEVEL_ERROR, message, details));
    }
}

#[derive(Clone, serde::Serialize)]
pub(crate) struct LogEntry {
    level: &'static str,
    message: String,
    details: Option<String>,
}

impl LogEntry {
    const LEVEL_DEBUG: &'static str = "debug";
    const LEVEL_INFO: &'static str = "info";
    const LEVEL_WARN: &'static str = "warn";
    const LEVEL_ERROR: &'static str = "error";

    fn new(
        level: &'static str,
        message: impl Into<String>,
        details: Option<impl Into<String>>,
    ) -> Self {
        Self {
            level,
            message: message.into(),
            details: details.map(Into::into),
        }
    }
}

impl Logger for AppHandle {
    fn log(&self, entry: &LogEntry) {
        if let Err(e) = self.emit_all("log", entry) {
            error!("Emitting a log event faild: {}", e);
        }
    }
}
