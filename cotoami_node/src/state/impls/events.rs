use anyhow::Result;
use cotoami_db::prelude::*;
use eventsource_stream::Event;
use tracing::{info, warn};

use crate::{service::NodeService, state::NodeState};

impl NodeState {
    pub async fn handle_event<S>(
        &mut self,
        source_node_id: Id<Node>,
        event: &Event,
        source_service: &mut S,
    ) -> Result<()>
    where
        S: NodeService + Send,
        S::Future: Send,
    {
        match &*event.event {
            "change" => {
                let change = serde_json::from_str::<ChangelogEntry>(&event.data)?;
                info!(
                    "Received a change {} from {}",
                    change.serial_number,
                    source_service.description()
                );
                self.handle_parent_change(source_node_id, change, source_service)
                    .await?;
            }
            _ => warn!("Unknown event: {}", event.event),
        }
        Ok(())
    }
}
