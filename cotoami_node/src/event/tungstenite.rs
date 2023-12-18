use std::{future::Future, marker::Unpin, ops::ControlFlow};

use bytes::Bytes;
use cotoami_db::{Id, Node};
use futures::{Sink, SinkExt, Stream, StreamExt};
use tokio::task::{AbortHandle, JoinSet};
use tokio_tungstenite::tungstenite::protocol::Message;
use tracing::{debug, error, info};

use crate::{event::NodeSentEvent, service::PubsubService, state::NodeState};

pub(crate) async fn handle_parent<TStream, StreamErr, TSink, SinkErr>(
    parent_id: Id<Node>,
    description: &str,
    stream: TStream,
    mut sink: TSink,
    state: &NodeState,
    abortables: &mut Vec<AbortHandle>,
) where
    TStream: Stream<Item = Result<Message, StreamErr>> + Unpin + Send + 'static,
    StreamErr: Into<anyhow::Error> + Send + 'static,
    TSink: Sink<Message, Error = SinkErr> + Unpin + Send + 'static,
    SinkErr: Into<anyhow::Error>,
{
    let mut tasks = JoinSet::new();

    // Register a parent service
    let parent_service = PubsubService::new(description, state.pubsub().responses().clone());
    state.put_parent_service(parent_id, Box::new(parent_service.clone()));

    // A task sending request events
    abortables.push(tasks.spawn({
        let mut requests = parent_service.requests().subscribe(None::<()>);
        async move {
            while let Some(request) = requests.next().await {
                let event = NodeSentEvent::Request(request);
                if let Err(e) = send_event(&mut sink, event).await {
                    debug!("Parent ({}) disconnected: {}", parent_id, e.into());
                    break;
                }
            }
        }
    }));

    // A task receiving events from the parent
    abortables.push(tasks.spawn(handle_message_stream(stream, parent_id, {
        let state = state.clone();
        move |event| super::handle_event_from_parent(event, parent_id, state.clone())
    })));

    // Sync with the parent after tasks are setup.
    if let Some(parent_service) = state.parent_service(&parent_id) {
        if let Err(e) = state.sync_with_parent(parent_id, parent_service).await {
            error!("Error syncing with ({}): {}", description, e);
            tasks.shutdown().await;
            return;
        }
    }

    // If any one of the tasks exit, abort the other.
    if let Some(_) = tasks.join_next().await {
        tasks.shutdown().await;
        state
            .pubsub()
            .events()
            .publish_parent_disconnected(parent_id);
    }
}

async fn send_event<S, E>(mut message_sink: S, event: NodeSentEvent) -> Result<(), E>
where
    S: Sink<Message, Error = E> + Unpin,
{
    let bytes = rmp_serde::to_vec(&event)
        .map(Bytes::from)
        .expect("A NodeSentEvent should be serializable into MessagePack");
    message_sink.send(Message::Binary(bytes.into())).await
}

async fn handle_message_stream<S, E, H, F>(mut stream: S, peer_id: Id<Node>, handler: H)
where
    S: Stream<Item = Result<Message, E>> + Unpin,
    E: Into<anyhow::Error>,
    H: Fn(NodeSentEvent) -> F,
    F: Future<Output = ControlFlow<anyhow::Error>>,
{
    while let Some(Ok(msg)) = stream.next().await {
        match msg {
            Message::Binary(vec) => match rmp_serde::from_slice::<NodeSentEvent>(&vec) {
                Ok(event) => {
                    if handler(event).await.is_break() {
                        break;
                    }
                }
                Err(e) => {
                    // A malicious client can send an invalid message intentionally,
                    // so let's not handle it as an error.
                    info!("The peer ({peer_id}) sent an invalid binary message: {e}");
                    break;
                }
            },
            Message::Close(c) => {
                info!("The peer ({peer_id}) sent close with: {c:?}");
                break;
            }
            the_others => debug!("Message ignored: {:?}", the_others),
        }
    }
}
