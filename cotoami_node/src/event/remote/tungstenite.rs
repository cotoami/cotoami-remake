use std::{future::Future, marker::Unpin, ops::ControlFlow, sync::Arc};

use bytes::Bytes;
use cotoami_db::{Id, Node, Operator};
use futures::{Sink, SinkExt, Stream, StreamExt};
use parking_lot::Mutex;
use tokio::{
    sync::{mpsc, mpsc::Sender},
    task::{AbortHandle, JoinSet},
};
use tokio_tungstenite::tungstenite::protocol::Message;
use tracing::debug;

use crate::{
    event::remote::{CommunicationError, NodeSentEvent},
    service::PubsubService,
    state::NodeState,
    Abortables,
};

/// Spawn and join tasks to handle a WebSocket connection to a parent node.
pub(crate) async fn communicate_with_parent<MsgSink, MsgSinkErr, MsgStream, MsgStreamErr, OnAbort>(
    node_state: NodeState,
    parent_id: Id<Node>,
    description: String,
    msg_sink: MsgSink,
    msg_stream: MsgStream,
    mut on_abort: OnAbort,
    abortables: Abortables,
) where
    MsgSink: Sink<Message, Error = MsgSinkErr> + Unpin + Send + 'static,
    MsgSinkErr: Into<anyhow::Error>,
    MsgStream: Stream<Item = Result<Message, MsgStreamErr>> + Unpin + Send + 'static,
    MsgStreamErr: Into<anyhow::Error> + Send + 'static,
    OnAbort: Sink<Option<CommunicationError>> + Unpin,
{
    let mut tasks = JoinSet::new();
    let task_error = Arc::new(Mutex::new(None::<CommunicationError>));

    // Create a parent service.
    let parent_service = PubsubService::new(description, node_state.pubsub().responses().clone());

    // A task sending messages received from the other tasks
    let (msg_sender, handle) = task_sending_messages(&mut tasks, msg_sink, task_error.clone());
    abortables.add(handle);

    // A task sending request events.
    abortables.add(tasks.spawn({
        let msg_sender = msg_sender.clone();
        let mut requests = parent_service.requests().subscribe(None::<()>);
        async move {
            while let Some(request) = requests.next().await {
                if msg_sender
                    .send(NodeSentEvent::Request(request).into())
                    .await
                    .is_err()
                {
                    break; // task_sending_messages has been terminated
                }
            }
        }
    }));

    // A task receiving events from the parent.
    abortables.add(tasks.spawn(handle_message_stream(
        msg_stream,
        Some(parent_id),
        task_error.clone(),
        {
            let node_state = node_state.clone();
            move |event| super::handle_event_from_parent(event, parent_id, node_state.clone())
        },
    )));

    // Register the parent service after wired up by tasks.
    node_state.register_parent_service(parent_id, Box::new(parent_service.clone()));

    // If any one of the tasks exit, abort the other.
    if tasks.join_next().await.is_some() {
        tasks.shutdown().await;
        on_abort
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
    OnAbort,
>(
    node_state: NodeState,
    opr: Arc<Operator>,
    msg_sink: MsgSink,
    msg_stream: MsgStream,
    mut on_abort: OnAbort,
    abortables: Abortables,
) where
    MsgSink: Sink<Message, Error = MsgSinkErr> + Unpin + Send + 'static,
    MsgSinkErr: Into<anyhow::Error>,
    MsgStream: Stream<Item = Result<Message, MsgStreamErr>> + Unpin + Send + 'static,
    MsgStreamErr: Into<anyhow::Error> + Send + 'static,
    OnAbort: Sink<Option<CommunicationError>> + Unpin,
{
    let node_id = opr.node_id();

    let mut tasks = JoinSet::new();
    let task_error = Arc::new(Mutex::new(None::<CommunicationError>));

    // A task sending messages received from the other tasks
    let (msg_sender, handle) = task_sending_messages(&mut tasks, msg_sink, task_error.clone());
    abortables.add(handle);

    // A task publishing change events to the operator
    abortables.add(tasks.spawn({
        let msg_sender = msg_sender.clone();
        let mut changes = node_state.pubsub().changes().subscribe(None::<()>);
        async move {
            while let Some(change) = changes.next().await {
                if msg_sender
                    .send(NodeSentEvent::Change(change).into())
                    .await
                    .is_err()
                {
                    break; // task_sending_messages has been terminated
                }
            }
        }
    }));

    // A task publishing local events to the operator who has owner privilege
    if opr.has_owner_permission() {
        abortables.add(tasks.spawn({
            let msg_sender = msg_sender.clone();
            let mut events = node_state.pubsub().events().subscribe(None::<()>);
            async move {
                while let Some(event) = events.next().await {
                    if msg_sender
                        .send(NodeSentEvent::RemoteLocal(event).into())
                        .await
                        .is_err()
                    {
                        break; // task_sending_messages has been terminated
                    }
                }
            }
        }));
    }

    // A task receiving events from the child
    abortables.add(tasks.spawn({
        handle_message_stream(msg_stream, node_id, task_error.clone(), {
            move |event| {
                super::handle_event_from_operator(
                    event,
                    opr.clone(),
                    node_state.clone(),
                    Box::pin(as_event_sink(msg_sender.clone())),
                )
            }
        })
    }));

    // If any one of the tasks exit, abort the others.
    if tasks.join_next().await.is_some() {
        tasks.shutdown().await;
        on_abort
            .send(Arc::try_unwrap(task_error).unwrap().into_inner())
            .await
            .ok();
    }
}

const SEND_BUFFER_SIZE: usize = 16;

fn task_sending_messages<MsgSink, MsgSinkErr>(
    tasks: &mut JoinSet<()>,
    mut msg_sink: MsgSink,
    task_error: Arc<Mutex<Option<CommunicationError>>>,
) -> (Sender<Message>, AbortHandle)
where
    MsgSink: Sink<Message, Error = MsgSinkErr> + Unpin + Send + 'static,
    MsgSinkErr: Into<anyhow::Error>,
{
    let (sender, mut receiver) = mpsc::channel::<Message>(SEND_BUFFER_SIZE);
    let handle = tasks.spawn({
        let task_error = task_error.clone();
        async move {
            while let Some(message) = receiver.recv().await {
                if let Err(e) = msg_sink.send(message).await {
                    task_error
                        .lock()
                        .replace(CommunicationError::Connection(e.into()));
                    break;
                }
            }
        }
    });
    (sender, handle)
}

fn as_event_sink(
    sender: Sender<Message>,
) -> impl Sink<NodeSentEvent, Error = anyhow::Error> + 'static {
    futures::sink::unfold((), move |(), event: NodeSentEvent| {
        let sender = sender.clone();
        async move { sender.send(event.into()).await.map_err(anyhow::Error::from) }
    })
}

impl From<NodeSentEvent> for Message {
    fn from(event: NodeSentEvent) -> Self {
        let bytes = rmp_serde::to_vec(&event)
            .map(Bytes::from)
            .expect("A NodeSentEvent should be serializable into MessagePack");
        Message::Binary(bytes.into())
    }
}

/// Read WebSocket messages as [NodeSentEvent]s and handle them with the given `handler`.
async fn handle_message_stream<MsgStream, MsgStreamErr, H, F>(
    mut msg_stream: MsgStream,
    peer_id: Option<Id<Node>>,
    error: Arc<Mutex<Option<CommunicationError>>>,
    handler: H,
) where
    MsgStream: Stream<Item = Result<Message, MsgStreamErr>> + Unpin,
    MsgStreamErr: Into<anyhow::Error>,
    H: Fn(NodeSentEvent) -> F,
    F: Future<Output = ControlFlow<anyhow::Error>>,
{
    loop {
        match msg_stream.next().await {
            Some(Ok(msg)) => match msg {
                Message::Binary(vec) => match rmp_serde::from_slice::<NodeSentEvent>(&vec) {
                    Ok(event) => {
                        if let ControlFlow::Break(e) = handler(event).await {
                            error.lock().replace(CommunicationError::EventHandling(e));
                            break;
                        }
                    }
                    Err(e) => {
                        debug!("The peer ({peer_id:?}) sent an invalid binary message: {e}");
                        error
                            .lock()
                            .replace(CommunicationError::EventHandling(e.into()));
                        break;
                    }
                },
                Message::Close(c) => {
                    debug!("The peer ({peer_id:?}) sent close with: {c:?}");
                    break;
                }
                the_others => debug!("Message ignored: {:?}", the_others),
            },
            Some(Err(e)) => {
                // Currently, manual disconnection produces the following error:
                // "WebSocket protocol error: Connection reset without closing handshake"
                let anyhow_error = e.into();
                debug!("Message stream error: {anyhow_error:?}");
                error
                    .lock()
                    .replace(CommunicationError::Connection(anyhow_error));
                break;
            }
            None => {
                debug!("Message stream end");
                break;
            }
        }
    }
}
