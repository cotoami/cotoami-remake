use log::error;
use tauri::{AppHandle, Emitter};

pub(crate) trait MessageSink {
    fn send_message(&self, message: &Message);

    fn info<'a>(&self, message: &'a str, details: Option<&'a str>) {
        self.send_message(&Message::new(Message::CATEGORY_INFO, message, details));
    }
    fn error<'a>(&self, message: &'a str, details: Option<&'a str>) {
        self.send_message(&Message::new(Message::CATEGORY_ERROR, message, details));
    }
}

#[derive(Debug, Clone, serde::Serialize)]
pub(crate) struct Message<'a> {
    category: &'static str,
    message: &'a str,
    details: Option<&'a str>,
}

impl<'a> Message<'a> {
    const CATEGORY_INFO: &'static str = "info";
    const CATEGORY_ERROR: &'static str = "error";

    fn new(category: &'static str, message: &'a str, details: Option<&'a str>) -> Self {
        Self {
            category,
            message,
            details,
        }
    }
}

impl MessageSink for AppHandle {
    fn send_message(&self, message: &Message) {
        if let Err(e) = self.emit("message", message) {
            error!("Emitting a message faild: {:?} (reason: {})", message, e);
        }
    }
}
