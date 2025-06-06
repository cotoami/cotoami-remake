use cotoami_db::prelude::*;
use cotoami_node::{prelude::*, Abortables};
use futures::StreamExt;
use tauri::{AppHandle, Emitter};
use tokio::task::JoinSet;
use tracing::error;

/////////////////////////////////////////////////////////////////////////////
// Emit changes
/////////////////////////////////////////////////////////////////////////////

pub(crate) trait ChangeSink {
    fn send_backend_change(&self, change: &ChangelogEntry);
}

impl ChangeSink for AppHandle {
    fn send_backend_change(&self, change: &ChangelogEntry) {
        if let Err(e) = self.emit("backend-change", change) {
            error!("Emitting a backend change faild: {e}");
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
        if let Err(e) = self.emit("backend-event", event) {
            error!("Emitting a backend event faild: {e}");
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
            let parent_events = state.pubsub().remote_events().subscribe(Some(parent_id));

            // From local events, send only parent-server state changes for the frontend
            // to be able to detect disconnection from the parent the app is operating on.
            let local_events = state
                .pubsub()
                .events()
                .subscribe(None::<()>)
                .filter(move |e| {
                    futures::future::ready(match e {
                        LocalNodeEvent::ServerStateChanged { node_id, .. } => node_id == &parent_id,
                        _ => false,
                    })
                });

            futures::stream::select(parent_events, local_events).boxed()
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
