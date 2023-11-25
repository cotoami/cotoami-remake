use std::{convert::Infallible, sync::Arc};

use axum::response::sse::Event as SseEvent;
use cotoami_db::prelude::*;

use crate::{pubsub::Publisher, service::models::NotConnected};

#[derive(Clone)]
pub struct Pubsub {
    pub local_changes: Arc<ChangePubsub>,
    pub sse_changes: Arc<SsePubsub>,
    pub events: Arc<EventPubsub>,
}

impl Pubsub {
    pub(crate) fn new() -> Self {
        let local_changes = ChangePubsub::new();
        let sse_changes = SsePubsub::new();
        let events = EventPubsub::new();

        sse_changes.tap_into(
            local_changes.subscribe(None::<()>),
            |_| None,
            |change| {
                let event = SseEvent::default().event("change").json_data(change)?;
                Ok(Ok(event))
            },
        );

        Self {
            local_changes: Arc::new(local_changes),
            sse_changes: Arc::new(sse_changes),
            events: Arc::new(events),
        }
    }

    pub fn publish_change(&self, changelog: ChangelogEntry) {
        self.local_changes.publish(changelog, None);
    }
}

pub(crate) type ChangePubsub = Publisher<ChangelogEntry, ()>;
pub(crate) type SsePubsub = Publisher<Result<SseEvent, Infallible>, ()>;

pub type EventPubsub = Publisher<Event, ()>;

#[derive(Clone)]
pub enum Event {
    ServerDisconnected {
        server_node_id: Id<Node>,
        reason: NotConnected,
    },
    ParentDisconnected(Id<Node>),
}
