use cotoami_db::prelude::*;

use crate::{event::local::LocalNodeEvent, service::models::NotConnected, state::NodeState};

impl NodeState {
    pub(crate) fn server_state_changed(
        &self,
        node_id: Id<Node>,
        before: Option<NotConnected>,
        after: Option<NotConnected>,
        client_as_child: Option<ChildNode>,
    ) -> bool {
        match (before, after) {
            (Some(_), None) => {
                self.server_connected(node_id, client_as_child);
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
                        client_as_child,
                    });
                true
            }
            _ => false, // state unchanged
        }
    }

    fn server_connected(&self, node_id: Id<Node>, client_as_child: Option<ChildNode>) {
        self.pubsub()
            .publish_event(LocalNodeEvent::ServerStateChanged {
                node_id,
                not_connected: None,
                client_as_child,
            });
    }

    fn server_disconnected(&self, node_id: Id<Node>, not_connected: NotConnected) {
        self.pubsub()
            .publish_event(LocalNodeEvent::ServerStateChanged {
                node_id,
                not_connected: Some(not_connected),
                client_as_child: None,
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
