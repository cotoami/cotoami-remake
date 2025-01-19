use cotoami_db::prelude::*;

use crate::{
    event::local::LocalNodeEvent,
    pubsub::Publisher,
    service::{models::NotConnected, pubsub::ResponsePubsub},
};

/////////////////////////////////////////////////////////////////////////////
// Pubsub aggregation
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, Default)]
pub struct Pubsub {
    changes: ChangePubsub,
    remote_changes: RemoteChangePubsub,
    events: EventPubsub,
    remote_events: RemoteEventPubsub,
    responses: ResponsePubsub,
}

impl Pubsub {
    pub fn changes(&self) -> &ChangePubsub { &self.changes }

    pub fn remote_changes(&self) -> &RemoteChangePubsub { &self.remote_changes }

    pub fn events(&self) -> &EventPubsub { &self.events }

    pub fn remote_events(&self) -> &RemoteEventPubsub { &self.remote_events }

    pub fn responses(&self) -> &ResponsePubsub { &self.responses }

    pub fn publish_change(&self, changelog: ChangelogEntry) {
        self.changes.publish(changelog, None);
    }

    pub fn publish_event(&self, event: LocalNodeEvent) { self.events.publish(event, None); }
}

/////////////////////////////////////////////////////////////////////////////
// ChangePubsub
/////////////////////////////////////////////////////////////////////////////

pub(crate) type ChangePubsub = Publisher<ChangelogEntry, ()>;

pub(crate) type RemoteChangePubsub = Publisher<ChangelogEntry, Id<Node>>;

/////////////////////////////////////////////////////////////////////////////
// EventPubsub
/////////////////////////////////////////////////////////////////////////////

pub type EventPubsub = Publisher<LocalNodeEvent, ()>;

impl EventPubsub {
    pub fn server_connected(&self, node_id: Id<Node>, client_as_child: Option<ChildNode>) {
        self.publish(
            LocalNodeEvent::ServerStateChanged {
                node_id,
                not_connected: None,
                client_as_child,
            },
            None,
        );
    }

    pub fn server_disconnected(
        &self,
        node_id: Id<Node>,
        not_connected: NotConnected,
        is_parent: bool,
    ) {
        self.publish(
            LocalNodeEvent::ServerStateChanged {
                node_id,
                not_connected: Some(not_connected),
                client_as_child: None,
            },
            None,
        );
        if is_parent {
            self.parent_disconnected(node_id);
        }
    }

    pub fn parent_disconnected(&self, node_id: Id<Node>) {
        self.publish(LocalNodeEvent::ParentDisconnected { node_id }, None);
    }
}

pub type RemoteEventPubsub = Publisher<LocalNodeEvent, Id<Node>>;
