use anyhow::Result;
use cotoami_db::prelude::*;
use eventsource_stream::Event;
use tower_service::Service;
use tracing::{debug, warn};

use crate::{
    service::{NodeService, Request, Response},
    state::NodeState,
};

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
                debug!(
                    "Received a change from {}: {}",
                    source_service.description(),
                    change.serial_number
                );
                self.handle_parent_change(source_node_id, change, source_service)
                    .await?;
            }
            "request" => {
                let request = serde_json::from_str::<Request>(&event.data)?;
                debug!(
                    "Received a request from {}: {:?}",
                    source_service.description(),
                    request
                );
                // Handle the request by this node
                let response = self.call(request).await?;
                // TODO: POST /api/responses
            }
            "response" => {
                let response = serde_json::from_str::<Response>(&event.data)?;
                debug!("Received a response from {}", source_service.description());
                // TODO: Response Pubsub?
            }
            _ => warn!("Unknown event: {}", event.event),
        }
        Ok(())
    }
}
