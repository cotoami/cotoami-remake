use cotoami_db::prelude::*;

use crate::{
    event::local::LocalNodeEvent,
    pubsub::Publisher,
    service::{models::NotConnected, pubsub::ResponsePubsub},
};

/////////////////////////////////////////////////////////////////////////////
// Pubsub aggregation
/////////////////////////////////////////////////////////////////////////////

#[derive(Clone)]
pub struct Pubsub {
    local_changes: ChangePubsub,
    events: EventPubsub,
    responses: ResponsePubsub,
}

impl Pubsub {
    pub(crate) fn new() -> Self {
        let local_changes = ChangePubsub::new();
        let events = EventPubsub::new();
        let responses = ResponsePubsub::new();

        Self {
            local_changes,
            events,
            responses,
        }
    }

    pub fn local_changes(&self) -> &ChangePubsub { &self.local_changes }

    pub fn events(&self) -> &EventPubsub { &self.events }

    pub fn responses(&self) -> &ResponsePubsub { &self.responses }

    pub fn publish_change(&self, changelog: ChangelogEntry) {
        self.local_changes.publish(changelog, None);
    }

    pub fn publish_event(&self, event: LocalNodeEvent) { self.events.publish(event, None); }
}

/////////////////////////////////////////////////////////////////////////////
// ChangePubsub
/////////////////////////////////////////////////////////////////////////////

pub(crate) type ChangePubsub = Publisher<ChangelogEntry, ()>;

/////////////////////////////////////////////////////////////////////////////
// EventPubsub
/////////////////////////////////////////////////////////////////////////////

pub type EventPubsub = Publisher<LocalNodeEvent, ()>;

impl EventPubsub {
    pub fn publish_server_disconnected(
        &self,
        node_id: Id<Node>,
        reason: NotConnected,
        is_parent: bool,
    ) {
        self.publish(LocalNodeEvent::ServerDisconnected { node_id, reason }, None);
        if is_parent {
            self.publish_parent_disconnected(node_id);
        }
    }

    pub fn publish_parent_disconnected(&self, node_id: Id<Node>) {
        self.publish(LocalNodeEvent::ParentDisconnected(node_id), None);
    }
}
