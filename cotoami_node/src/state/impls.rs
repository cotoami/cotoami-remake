use anyhow::Result;
use futures::StreamExt;
use tracing::debug;

use crate::state::{pubsub::Event, NodeState};

mod changes;
mod nodes;
mod parents;
mod servers;
mod session;

impl NodeState {
    pub async fn prepare(&self) -> Result<()> {
        self.init_local_node().await?;
        self.restore_server_conns().await?;
        self.set_internal_event_handler();
        Ok(())
    }

    fn set_internal_event_handler(&self) {
        let this = self.clone();
        tokio::spawn(async move {
            let mut events = this.pubsub().events().subscribe(None::<()>);
            while let Some(event) = events.next().await {
                debug!("Internal event: {event:?}");
                match event {
                    Event::ParentDisconnected(parent_id) => {
                        this.remove_parent_service(&parent_id);
                    }
                    _ => (),
                }
            }
        });
    }
}
