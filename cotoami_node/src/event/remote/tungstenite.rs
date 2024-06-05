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
use tracing::{debug, info};

use crate::{
    event::remote::{EventLoopError, NodeSentEvent},
    service::PubsubService,
    state::NodeState,
};

/// Spawn and join tasks to handle a WebSocket connection to a parent node.
pub(crate) async fn communicate_with_parent<
    MsgSink,
    MsgSinkErr,
    MsgStream,
    MsgStreamErr,
    OnDisconnect,
>(
    node_state: NodeState,
    parent_id: Id<Node>,
    description: String,
    mut msg_sink: MsgSink,
    msg_stream: MsgStream,
    mut on_disconnect: OnDisconnect,
    abortables: Arc<Mutex<Vec<AbortHandle>>>,
) where
    MsgSink: Sink<Message, Error = MsgSinkErr> + Unpin + Send + 'static,
    MsgSinkErr: Into<anyhow::Error>,
    MsgStream: Stream<Item = Result<Message, MsgStreamErr>> + Unpin + Send + 'static,
    MsgStreamErr: Into<anyhow::Error> + Send + 'static,
    OnDisconnect: Sink<Option<EventLoopError>> + Unpin + Clone + Send + 'static,
{
    let mut tasks = JoinSet::new();
    let task_error = Arc::new(Mutex::new(None::<EventLoopError>));

    // Create a parent service.
    let parent_service =
        PubsubService::new(description.clone(), node_state.pubsub().responses().clone());

    // A task sending request events.
    abortables.lock().push(tasks.spawn({
        let mut requests = parent_service.requests().subscribe(None::<()>);
        let task_error = task_error.clone();
        async move {
            while let Some(request) = requests.next().await {
                let event = NodeSentEvent::Request(request);
                if let Err(e) = send_event(&mut msg_sink, event).await {
                    task_error
                        .lock()
                        .replace(EventLoopError::CommunicationFailed(e.into()));
                    break;
                }
            }
        }
    }));

    // A task receiving events from the parent.
    abortables.lock().push(tasks.spawn(handle_message_stream(
        msg_stream,
        parent_id,
        task_error.clone(),
        {
            let node_state = node_state.clone();
            move |event| super::handle_event_from_parent(event, parent_id, node_state.clone())
        },
    )));

    // Register the parent service after wired up by tasks.
    node_state.register_parent_service(parent_id, Box::new(parent_service.clone()));

    // If any one of the tasks exit, abort the other.
    if let Some(_) = tasks.join_next().await {
        tasks.shutdown().await;
        on_disconnect
            .send(Arc::try_unwrap(task_error).unwrap().into_inner())
            .await
            .ok();
    }
}

/// Spawn and join tasks to handle a WebSocket connection to a child node.
pub(crate) async fn communicate_with_operator<
    MsgSink,
    MsgSinkErr,
    MsgStream,
    MsgStreamErr,
    OnDisconnect,
>(
    node_state: NodeState,
    opr: Arc<Operator>,
    mut msg_sink: MsgSink,
    msg_stream: MsgStream,
    mut on_disconnect: OnDisconnect,
    abortables: Arc<Mutex<Vec<AbortHandle>>>,
) where
    MsgSink: Sink<Message, Error = MsgSinkErr> + Unpin + Send + 'static,
    MsgSinkErr: Into<anyhow::Error>,
    MsgStream: Stream<Item = Result<Message, MsgStreamErr>> + Unpin + Send + 'static,
    MsgStreamErr: Into<anyhow::Error> + Send + 'static,
    OnDisconnect: Sink<Option<EventLoopError>> + Unpin + Clone + Send + 'static,
{
    let node_id = opr.node_id();

    let (sender, mut receiver) = mpsc::channel::<NodeSentEvent>(SEND_BUFFER_SIZE);
    let mut tasks = JoinSet::new();
    let task_error = Arc::new(Mutex::new(None::<EventLoopError>));

    // A task sending events received from the other tasks
    abortables.lock().push(tasks.spawn({
        let task_error = task_error.clone();
        async move {
            while let Some(event) = receiver.recv().await {
                if let Err(e) = send_event(&mut msg_sink, event).await {
                    task_error
                        .lock()
                        .replace(EventLoopError::CommunicationFailed(e.into()));
                    break;
                }
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
        handle_message_stream(msg_stream, node_id, task_error.clone(), move |event| {
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
        on_disconnect
            .send(Arc::try_unwrap(task_error).unwrap().into_inner())
            .await
            .ok();
    }
}

/// The size of the buffer used to send events in in [handle_operator].
///
/// The events to be sent in [handle_operator]:
/// * local changes (which contains the changes from the parents of the local node)
/// * responses (correlating with the number of children sending requests)
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
async fn handle_message_stream<MsgStream, MsgStreamErr, H, F>(
    mut msg_stream: MsgStream,
    peer_id: Id<Node>,
    error: Arc<Mutex<Option<EventLoopError>>>,
    handler: H,
) where
    MsgStream: Stream<Item = Result<Message, MsgStreamErr>> + Unpin,
    MsgStreamErr: Into<anyhow::Error>,
    H: Fn(NodeSentEvent) -> F,
    F: Future<Output = ControlFlow<anyhow::Error>>,
{
    while let Some(Ok(msg)) = msg_stream.next().await {
        match msg {
            Message::Binary(vec) => match rmp_serde::from_slice::<NodeSentEvent>(&vec) {
                Ok(event) => {
                    if let ControlFlow::Break(e) = handler(event).await {
                        error.lock().replace(EventLoopError::EventHandlingFailed(e));
                        break;
                    }
                }
                Err(e) => {
                    info!("The peer ({peer_id}) sent an invalid binary message: {e}");
                    error
                        .lock()
                        .replace(EventLoopError::EventHandlingFailed(e.into()));
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
