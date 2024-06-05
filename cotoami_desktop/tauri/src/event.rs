use cotoami_db::ChangelogEntry;
use cotoami_node::prelude::*;
use futures::StreamExt;
use tauri::{AppHandle, Manager};
use tokio::task::JoinSet;

use crate::log::Logger;

/////////////////////////////////////////////////////////////////////////////
// Emit local changes
/////////////////////////////////////////////////////////////////////////////

pub(crate) trait LocalChangeSink {
    fn send_backend_change(&self, change: &ChangelogEntry);
}

impl LocalChangeSink for AppHandle {
    fn send_backend_change(&self, change: &ChangelogEntry) {
        if let Err(e) = self.emit_all("backend-change", change) {
            self.error(
                &format!("Emitting a backend change faild: {e}"),
                Some(&format!("{change:?}")),
            );
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Emit local events
/////////////////////////////////////////////////////////////////////////////

pub(crate) trait LocalEventSink {
    fn send_backend_event(&self, event: &LocalNodeEvent);
}

impl LocalEventSink for AppHandle {
    fn send_backend_event(&self, event: &LocalNodeEvent) {
        if let Err(e) = self.emit_all("backend-event", event) {
            self.error(
                &format!("Emitting a backend event faild: {e}"),
                Some(&format!("{event:?}")),
            );
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Wire up between frontend and backend
/////////////////////////////////////////////////////////////////////////////

pub(super) async fn listen(state: NodeState, app_handle: AppHandle) {
    let mut tasks = JoinSet::new();

    // listen to [ChangelogEntry]s
    tasks.spawn({
        let (state, app_handle) = (state.clone(), app_handle.clone());
        let mut changes = state.pubsub().local_changes().subscribe(None::<()>);
        async move {
            while let Some(change) = changes.next().await {
                app_handle.send_backend_change(&change);
            }
        }
    });

    // listen to [LocalNodeEvent]s
    tasks.spawn({
        let (state, app_handle) = (state.clone(), app_handle.clone());
        let mut events = state.pubsub().events().subscribe(None::<()>);
        async move {
            while let Some(event) = events.next().await {
                app_handle.send_backend_event(&event);
            }
        }
    });

    // If any one of the tasks exit, abort the other.
    if let Some(_) = tasks.join_next().await {
        tasks.shutdown().await;
    }
}
