use log::error;
use tauri::{AppHandle, Manager};

pub(crate) trait Logger {
    fn log(&self, event: &LogEvent);

    fn debug<'a>(&self, message: &'a str, details: Option<&'a str>) {
        self.log(&LogEvent::new(LogEvent::LEVEL_DEBUG, message, details));
    }
    fn info<'a>(&self, message: &'a str, details: Option<&'a str>) {
        self.log(&LogEvent::new(LogEvent::LEVEL_INFO, message, details));
    }
    fn warn<'a>(&self, message: &'a str, details: Option<&'a str>) {
        self.log(&LogEvent::new(LogEvent::LEVEL_WARN, message, details));
    }
    fn error<'a>(&self, message: &'a str, details: Option<&'a str>) {
        self.log(&LogEvent::new(LogEvent::LEVEL_ERROR, message, details));
    }
}

#[derive(Debug, Clone, serde::Serialize)]
pub(crate) struct LogEvent<'a> {
    level: &'static str,
    message: &'a str,
    details: Option<&'a str>,
}

impl<'a> LogEvent<'a> {
    const LEVEL_DEBUG: &'static str = "debug";
    const LEVEL_INFO: &'static str = "info";
    const LEVEL_WARN: &'static str = "warn";
    const LEVEL_ERROR: &'static str = "error";

    fn new(level: &'static str, message: &'a str, details: Option<&'a str>) -> Self {
        Self {
            level,
            message,
            details,
        }
    }
}

impl Logger for AppHandle {
    fn log(&self, event: &LogEvent) {
        if let Err(e) = self.emit_all("log", event) {
            error!("Emitting a log event faild: {:?} (reason: {})", event, e);
        }
    }
}
