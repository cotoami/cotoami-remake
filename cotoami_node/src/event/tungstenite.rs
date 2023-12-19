use std::{future::Future, marker::Unpin, ops::ControlFlow, sync::Arc};

use bytes::Bytes;
use cotoami_db::{Id, Node, Operator};
use futures::{Sink, SinkExt, Stream, StreamExt};
use parking_lot::Mutex;
use tokio::{
    sync::mpsc,
    task::{AbortHandle, JoinSet},
};
use tokio_tungstenite::tungstenite::protocol::Message;
use tokio_util::sync::PollSender;
use tracing::{debug, error, info};

use crate::{event::NodeSentEvent, service::PubsubService, state::NodeState};

/// Spawn and join tasks to handle a WebSocket connection to a parent node.
pub(crate) async fn communicate_with_parent<MsgSink, MsgSinkErr, MsgStream, MsgStreamErr>(
    node_state: NodeState,
    parent_id: Id<Node>,
    description: String,
    mut msg_sink: MsgSink,
    msg_stream: MsgStream,
    abortables: Arc<Mutex<Vec<AbortHandle>>>,
) where
    MsgSink: Sink<Message, Error = MsgSinkErr> + Unpin + Send + 'static,
    MsgSinkErr: Into<anyhow::Error>,
    MsgStream: Stream<Item = Result<Message, MsgStreamErr>> + Unpin + Send + 'static,
    MsgStreamErr: Into<anyhow::Error> + Send + 'static,
{
    let mut tasks = JoinSet::new();

    // Register a parent service
    let parent_service =
        PubsubService::new(description.clone(), node_state.pubsub().responses().clone());
    node_state.put_parent_service(parent_id, Box::new(parent_service.clone()));

    // A task sending request events
    abortables.lock().push(tasks.spawn({
        let mut requests = parent_service.requests().subscribe(None::<()>);
        async move {
            while let Some(request) = requests.next().await {
                let event = NodeSentEvent::Request(request);
                if let Err(e) = send_event(&mut msg_sink, event).await {
                    debug!("Parent ({}) disconnected: {}", parent_id, e.into());
                    break;
                }
            }
        }
    }));

    // A task receiving events from the parent
    abortables
        .lock()
        .push(tasks.spawn(handle_message_stream(msg_stream, parent_id, {
            let node_state = node_state.clone();
            move |event| super::handle_event_from_parent(event, parent_id, node_state.clone())
        })));

    // Sync with the parent after tasks are setup.
    if let Some(parent_service) = node_state.parent_service(&parent_id) {
        if let Err(e) = node_state.sync_with_parent(parent_id, parent_service).await {
            error!("Error syncing with ({}): {}", description, e);
            tasks.shutdown().await;
            return;
        }
    }

    // If any one of the tasks exit, abort the other.
    if let Some(_) = tasks.join_next().await {
        tasks.shutdown().await;
        node_state
            .pubsub()
            .events()
            .publish_parent_disconnected(parent_id);
    }
}

/// Spawn and join tasks to handle a WebSocket connection to a child node.
pub(crate) async fn communicate_with_operator<MsgSink, MsgSinkErr, MsgStream, MsgStreamErr>(
    node_state: NodeState,
    opr: Arc<Operator>,
    mut msg_sink: MsgSink,
    msg_stream: MsgStream,
    abortables: Arc<Mutex<Vec<AbortHandle>>>,
) where
    MsgSink: Sink<Message, Error = MsgSinkErr> + Unpin + Send + 'static,
    MsgSinkErr: Into<anyhow::Error>,
    MsgStream: Stream<Item = Result<Message, MsgStreamErr>> + Unpin + Send + 'static,
    MsgStreamErr: Into<anyhow::Error> + Send + 'static,
{
    let node_id = opr.node_id();

    let (sender, mut receiver) = mpsc::channel::<NodeSentEvent>(SEND_BUFFER_SIZE);
    let mut tasks = JoinSet::new();

    // A task sending events received from the other tasks
    abortables.lock().push(tasks.spawn(async move {
        while let Some(event) = receiver.recv().await {
            if let Err(e) = send_event(&mut msg_sink, event).await {
                debug!("Operator ({}) disconnected: {}", node_id, e.into());
                break;
            }
        }
    }));

    // A task publishing change events to a child node
    if let Operator::ChildNode(_) = *opr {
        abortables.lock().push(tasks.spawn({
            let sender = sender.clone();
            let mut changes = node_state.pubsub().local_changes().subscribe(None::<()>);
            async move {
                while let Some(change) = changes.next().await {
                    if let Err(_) = sender.send(NodeSentEvent::Change(change)).await {
                        // The task above has been terminated
                        break;
                    }
                }
            }
        }));
    }

    // A task receiving events from the child
    abortables.lock().push(tasks.spawn({
        handle_message_stream(msg_stream, node_id, move |event| {
            super::handle_event_from_operator(
                event,
                opr.clone(),
                node_state.clone(),
                PollSender::new(sender.clone()),
            )
        })
    }));

    // If any one of the tasks exit, abort the others.
    if let Some(_) = tasks.join_next().await {
        tasks.shutdown().await;
    }
}

/// The size of the buffer used to send events in in [handle_operator].
///
/// The events to be sent in [handle_operator]:
/// * local changes (which contains the changes from the parents of the local node)
/// * responses　(correlating with the number of children sending requests)
const SEND_BUFFER_SIZE: usize = 16;

/// Send a [NodeSentEvent] to a peer (passed as a [Sink]) by converting it
/// to a tungstenite's [Message].
async fn send_event<S, E>(mut message_sink: S, event: NodeSentEvent) -> Result<(), E>
where
    S: Sink<Message, Error = E> + Unpin,
{
    let bytes = rmp_serde::to_vec(&event)
        .map(Bytes::from)
        .expect("A NodeSentEvent should be serializable into MessagePack");
    message_sink.send(Message::Binary(bytes.into())).await
}

/// Handle [NodeSentEvent]s streamed from a peer with a specified `handler`.
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
