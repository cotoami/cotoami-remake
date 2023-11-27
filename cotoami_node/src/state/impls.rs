use anyhow::Result;
use futures::StreamExt;
use tracing::error;

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
        self.stream_changes_to_child_servers();
        Ok(())
    }

    fn set_internal_event_handler(&self) {
        let this = self.clone();
        tokio::spawn(async move {
            let mut events = this.pubsub().events().subscribe(None::<()>);
            while let Some(event) = events.next().await {
                match event {
                    Event::ParentDisconnected(parent_id) => {
                        this.remove_parent_service(&parent_id);
                    }
                    _ => (),
                }
            }
        });
    }

    fn stream_changes_to_child_servers(&self) {
        let this = self.clone();
        tokio::spawn(async move {
            let mut changes = this.pubsub().local_changes().subscribe(None::<()>);
            while let Some(change) = changes.next().await {
                if let Err(e) = this.publish_change_to_child_servers(&change).await {
                    error!("Error during sending a change to child servers: {}", e);
                }
            }
        });
    }
}
