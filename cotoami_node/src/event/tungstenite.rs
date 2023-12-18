use std::{future::Future, marker::Unpin, ops::ControlFlow};

use bytes::Bytes;
use cotoami_db::{Id, Node};
use futures::{Sink, SinkExt, Stream, StreamExt};
use tokio_tungstenite::tungstenite::protocol::Message;
use tracing::{debug, info};

use crate::event::NodeSentEvent;

pub(crate) async fn handle_message_stream<S, E, H, F>(mut stream: S, peer_id: Id<Node>, handler: H)
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

pub(crate) async fn send_event<S, E>(mut message_sink: S, event: NodeSentEvent) -> Result<(), E>
where
    S: Sink<Message, Error = E> + Unpin,
{
    let bytes = rmp_serde::to_vec(&event)
        .map(Bytes::from)
        .expect("A NodeSentEvent should be serializable into MessagePack");
    message_sink.send(Message::Binary(bytes.into())).await
}
