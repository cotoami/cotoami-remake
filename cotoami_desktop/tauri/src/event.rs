use cotoami_db::prelude::*;
use cotoami_node::{prelude::*, Abortables};
use futures::StreamExt;
use tauri::{AppHandle, Manager};
use tokio::task::JoinSet;

use crate::log::Logger;

/////////////////////////////////////////////////////////////////////////////
// Emit changes
/////////////////////////////////////////////////////////////////////////////

pub(crate) trait ChangeSink {
    fn send_backend_change(&self, change: &ChangelogEntry);
}

impl ChangeSink for AppHandle {
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
// Emit node events
/////////////////////////////////////////////////////////////////////////////

pub(crate) trait NodeEventSink {
    fn send_backend_event(&self, event: &LocalNodeEvent);
}

impl NodeEventSink for AppHandle {
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
// Pipe backend events into the frontend
/////////////////////////////////////////////////////////////////////////////

pub(super) async fn pipe(
    parent_id: Option<Id<Node>>,
    state: NodeState,
    app_handle: AppHandle,
    abortables: Abortables,
) {
    let mut tasks = JoinSet::new();

    // ChangelogEntries
    abortables.add(tasks.spawn({
        let (state, app_handle) = (state.clone(), app_handle.clone());
        let mut changes = if let Some(parent_id) = parent_id {
            state
                .pubsub()
                .remote_changes()
                .subscribe(Some(parent_id))
                .boxed()
        } else {
            state.pubsub().changes().subscribe(None::<()>).boxed()
        };
        async move {
            while let Some(change) = changes.next().await {
                app_handle.send_backend_change(&change);
            }
        }
    }));

    // LocalNodeEvents
    abortables.add(tasks.spawn({
        let (state, app_handle) = (state.clone(), app_handle.clone());
        let mut events = if let Some(parent_id) = parent_id {
            state
                .pubsub()
                .remote_events()
                .subscribe(Some(parent_id))
                .boxed()
        } else {
            state.pubsub().events().subscribe(None::<()>).boxed()
        };
        async move {
            while let Some(event) = events.next().await {
                app_handle.send_backend_event(&event);
            }
        }
    }));

    // If any one of the tasks exit, abort the other.
    if tasks.join_next().await.is_some() {
        tasks.shutdown().await;
    }
}
