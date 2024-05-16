use cotoami_db::ChangelogEntry;
use log::error;
use tauri::{AppHandle, Manager};

use crate::log::Logger;

#[derive(Debug, serde::Serialize)]
pub(crate) enum BackendEvent {
    LocalChange(ChangelogEntry),
}

pub(crate) trait BackendEventSink {
    fn send(&self, event: &BackendEvent);
}

impl BackendEventSink for AppHandle {
    fn send(&self, event: &BackendEvent) {
        if let Err(e) = self.emit_all("backend-event", event) {
            error!("Emitting a backend event faild: {event:?} (reason: {e})");
            self.error(
                &format!("Emitting a backend event faild: {e}"),
                Some(&format!("{event:?}")),
            );
        }
    }
}
