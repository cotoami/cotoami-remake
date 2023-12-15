use std::{ops::ControlFlow, sync::Arc};

use cotoami_db::{ChangelogEntry, Operator};
use futures::{sink::Sink, SinkExt};
use tower_service::Service;
use tracing::{debug, error, info};

use crate::{
    service::{Request, Response},
    state::NodeState,
};

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub(crate) enum NodeSentEvent {
    Connected,
    Change(ChangelogEntry),
    Request(Request),
    Response(Response),
    Error(String),
}

pub(crate) async fn handle_event_from_operator<S>(
    event: NodeSentEvent,
    opr: Arc<Operator>,
    mut state: NodeState,
    mut sink: S,
) -> ControlFlow<(), ()>
where
    S: Sink<NodeSentEvent> + Unpin,
{
    match event {
        NodeSentEvent::Request(mut request) => {
            debug!("Received a request from: {:?}", opr);
            request.set_from(opr);
            match state.call(request).await {
                Ok(response) => {
                    if sink.send(NodeSentEvent::Response(response)).await.is_err() {
                        // Disconnected
                        return ControlFlow::Break(());
                    }
                }
                Err(e) => {
                    // It shouldn't happen: an error processing a request
                    // should be stored in a response.
                    error!("Unexpected error: {}", e);
                }
            }
        }
        unsupported => {
            info!("Parent doesn't support the event: {:?}", unsupported);
        }
    }
    ControlFlow::Continue(())
}
