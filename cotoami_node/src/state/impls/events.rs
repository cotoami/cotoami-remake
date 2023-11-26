use anyhow::Result;
use cotoami_db::prelude::*;
use tower_service::Service;
use tracing::{debug, warn};

use crate::{client::NodeSentEvent, service::NodeService, state::NodeState};

impl NodeState {
    pub async fn handle_node_sent_event(
        &mut self,
        source_node_id: Id<Node>,
        event: NodeSentEvent,
        source_service: Box<dyn NodeService>,
    ) -> Result<()> {
        match event {
            NodeSentEvent::Change(change) => {
                debug!(
                    "Received a change from {}: {}",
                    source_service.description(),
                    change.serial_number
                );
                self.handle_parent_change(source_node_id, change, source_service)
                    .await?;
            }
            NodeSentEvent::Request(request) => {
                debug!(
                    "Received a request from {}: {:?}",
                    source_service.description(),
                    request
                );
                // Handle the request by this node
                let response = self.call(request).await?;
                // TODO: POST /api/responses
            }
            NodeSentEvent::Response(response) => {
                debug!("Received a response from {}", source_service.description());
                // TODO: Response Pubsub?
            }
            NodeSentEvent::Error(msg) => warn!("Event error: {}", msg),
        }
        Ok(())
    }
}
