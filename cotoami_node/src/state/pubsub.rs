use cotoami_db::prelude::*;

use crate::{event::local::LocalNodeEvent, pubsub::Publisher, service::pubsub::ResponsePubsub};

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

pub type RemoteEventPubsub = Publisher<LocalNodeEvent, Id<Node>>;
