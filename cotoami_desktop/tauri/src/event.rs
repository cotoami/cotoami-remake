use cotoami_db::ChangelogEntry;
use cotoami_node::prelude::*;
use futures::StreamExt;
use log::error;
use tauri::{AppHandle, Manager};
use tokio::task::JoinSet;

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

pub(super) async fn listen(state: NodeState, app_handle: AppHandle) {
    let mut tasks = JoinSet::new();

    // listen to `BackendEvent::LocalChange`s.
    tasks.spawn({
        let (state, app_handle) = (state.clone(), app_handle.clone());
        let mut changes = state.pubsub().local_changes().subscribe(None::<()>);
        async move {
            while let Some(change) = changes.next().await {
                let event = BackendEvent::LocalChange(change);
                app_handle.send(&event);
            }
        }
    });

    // If any one of the tasks exit, abort the other.
    if let Some(_) = tasks.join_next().await {
        tasks.shutdown().await;
    }
}
