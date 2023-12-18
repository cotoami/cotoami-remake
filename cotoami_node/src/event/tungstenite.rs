use bytes::Bytes;
use futures::{Sink, SinkExt};
use tokio_tungstenite::tungstenite::protocol::Message;

use crate::event::NodeSentEvent;

pub(crate) async fn send_event<S, E>(mut message_sink: S, event: NodeSentEvent) -> Result<(), E>
where
    S: Sink<Message, Error = E> + Unpin,
{
    let bytes = rmp_serde::to_vec(&event)
        .map(Bytes::from)
        .expect("A NodeSentEvent should be serializable into MessagePack");
    message_sink.send(Message::Binary(bytes.into())).await
}
