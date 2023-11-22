use anyhow::Result;
use cotoami_db::prelude::*;
use tower_service::Service;
use tracing::{debug, warn};

use crate::{
    service::{Event, NodeService},
    state::NodeState,
};

impl NodeState {
    pub async fn handle_event<S>(
        &mut self,
        source_node_id: Id<Node>,
        event: Event,
        source_service: &mut S,
    ) -> Result<()>
    where
        S: NodeService + Send,
        S::Future: Send,
    {
        match event {
            Event::Change(change) => {
                debug!(
                    "Received a change from {}: {}",
                    source_service.description(),
                    change.serial_number
                );
                self.handle_parent_change(source_node_id, change, source_service)
                    .await?;
            }
            Event::Request(request) => {
                debug!(
                    "Received a request from {}: {:?}",
                    source_service.description(),
                    request
                );
                // Handle the request by this node
                let response = self.call(request).await?;
                // TODO: POST /api/responses
            }
            Event::Response(response) => {
                debug!("Received a response from {}", source_service.description());
                // TODO: Response Pubsub?
            }
            Event::Error(msg) => warn!("Event error: {}", msg),
        }
        Ok(())
    }
}
