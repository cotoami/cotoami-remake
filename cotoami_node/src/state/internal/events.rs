use std::sync::Arc;

use cotoami_db::prelude::*;
use futures::StreamExt;
use tracing::debug;

use crate::{event::local::LocalNodeEvent, service::models::NotConnected, state::NodeState};

impl NodeState {
    pub(crate) fn start_handling_local_events(&self) {
        let this = self.clone();
        self.spawn_task(async move {
            let mut events = this.pubsub().events().subscribe(None::<()>);
            while let Some(event) = events.next().await {
                debug!("Internal event: {event:?}");
                match event {
                    LocalNodeEvent::ServerStateChanged {
                        node_id,
                        not_connected: Some(NotConnected::SessionExpired),
                        ..
                    } => {
                        if let Ok(owner) = this.local_node_as_operator() {
                            debug!("Attempting to reconnect due to SessionExpired...");
                            this.reconnect_to_server(node_id, Arc::new(owner))
                                .await
                                .ok();
                        }
                    }

                    LocalNodeEvent::ParentDisconnected { node_id } => {
                        this.parent_services().remove(&node_id);
                    }

                    _ => (),
                }
            }
        });
    }

    pub(crate) fn server_state_changed(
        &self,
        node_id: Id<Node>,
        before: Option<NotConnected>,
        after: Option<NotConnected>,
        child_privileges: Option<ChildNode>,
    ) -> bool {
        match (before, after) {
            (Some(_), None) => {
                self.server_connected(node_id, child_privileges);
                true
            }
            (None, Some(not_connected)) => {
                self.server_disconnected(node_id, not_connected);
                true
            }
            (Some(before), Some(after)) if before != after => {
                self.pubsub()
                    .publish_event(LocalNodeEvent::ServerStateChanged {
                        node_id,
                        not_connected: Some(after),
                        child_privileges,
                    });
                true
            }
            _ => false, // state unchanged
        }
    }

    fn server_connected(&self, node_id: Id<Node>, child_privileges: Option<ChildNode>) {
        self.pubsub()
            .publish_event(LocalNodeEvent::ServerStateChanged {
                node_id,
                not_connected: None,
                child_privileges,
            });
    }

    fn server_disconnected(&self, node_id: Id<Node>, not_connected: NotConnected) {
        self.pubsub()
            .publish_event(LocalNodeEvent::ServerStateChanged {
                node_id,
                not_connected: Some(not_connected),
                child_privileges: None,
            });
        if self.is_parent(&node_id) {
            self.parent_disconnected(node_id);
        }
    }

    pub(crate) fn client_disconnected(&self, node_id: Id<Node>, error: Option<String>) {
        self.pubsub()
            .publish_event(LocalNodeEvent::ClientDisconnected { node_id, error });
        if self.is_parent(&node_id) {
            self.parent_disconnected(node_id);
        }
    }

    fn parent_disconnected(&self, node_id: Id<Node>) {
        self.pubsub()
            .events()
            .publish(LocalNodeEvent::ParentDisconnected { node_id }, None);
    }
}
