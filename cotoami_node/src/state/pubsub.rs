use std::{convert::Infallible, sync::Arc};

use axum::response::sse::Event;
use cotoami_db::prelude::*;

use crate::pubsub::Publisher;

#[derive(Clone)]
pub struct Pubsub {
    pub local_change: Arc<ChangePubsub>,
    pub sse_change: Arc<SsePubsub>,
}

impl Pubsub {
    pub(crate) fn new() -> Self {
        let local_change = ChangePubsub::new();
        let sse_change = SsePubsub::new();

        sse_change.tap_into(
            local_change.subscribe(None::<()>),
            |_| None,
            |change| {
                let event = Event::default().event("change").json_data(change)?;
                Ok(Ok(event))
            },
        );

        Self {
            local_change: Arc::new(local_change),
            sse_change: Arc::new(sse_change),
        }
    }

    pub fn publish_change(&self, changelog: ChangelogEntry) {
        self.local_change.publish(changelog, None);
    }
}

pub(crate) type ChangePubsub = Publisher<ChangelogEntry, ()>;
pub(crate) type SsePubsub = Publisher<Result<Event, Infallible>, ()>;
