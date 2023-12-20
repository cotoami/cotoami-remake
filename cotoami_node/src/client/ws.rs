//! WebSocket client of Node API Service.

use std::sync::Arc;

use anyhow::{bail, Result};
use cotoami_db::{Id, Node};
use futures::{Sink, StreamExt};
use tokio::sync::mpsc;
use tokio_tungstenite::connect_async;
use tokio_util::sync::PollSender;
use tracing::info;
use url::Url;

use crate::{
    client::{ClientState, ConnectionState},
    event::{
        tungstenite::{communicate_with_operator, communicate_with_parent},
        EventLoopError,
    },
    service::models::NotConnected,
    state::NodeState,
};

#[derive(Clone)]
pub struct WebSocketClient {
    state: Arc<ClientState>,
    ws_url: Url,
}

impl WebSocketClient {
    pub async fn new(
        server_id: Id<Node>,
        url_prefix: String,
        node_state: NodeState,
    ) -> Result<Self> {
        let state = ClientState::new(server_id, node_state).await?;
        let ws_url = Url::parse(&url_prefix)?.join("/api/ws")?;
        Ok(Self {
            state: Arc::new(state),
            ws_url,
        })
    }

    pub fn not_connected(&self) -> Option<NotConnected> { self.state.not_connected() }

    pub async fn connect(&mut self) -> Result<()> {
        if self.state.has_running_tasks() {
            bail!("Already connected");
        }
        let (sender, mut receiver) = mpsc::channel::<Option<EventLoopError>>(1);
        self.do_connect(PollSender::new(sender)).await?;
        tokio::spawn({
            let this = self.clone();
            async move {
                while let Some(err) = receiver.recv().await {
                    this.state
                        .set_conn_state(ConnectionState::Disconnected(err));
                    this.state.publish_server_disconnected();
                    // TODO: reconnect
                }
            }
        });
        Ok(())
    }

    async fn do_connect<S>(&mut self, on_disconnect: S) -> Result<()>
    where
        S: Sink<Option<EventLoopError>> + Unpin + Clone + Send + 'static,
    {
        let (ws_stream, _) = connect_async(&self.ws_url).await?;
        info!("WebSocket connection opened: {}", self.ws_url);
        self.state.set_conn_state(ConnectionState::Connected);

        let (sink, stream) = ws_stream.split();
        if let Some(opr) = self.state.server_as_operator.as_ref() {
            tokio::spawn(communicate_with_operator(
                self.state.node_state.clone(),
                opr.clone(),
                sink,
                stream,
                on_disconnect,
                self.state.abortables.clone(),
            ));
        } else {
            tokio::spawn(communicate_with_parent(
                self.state.node_state.clone(),
                self.state.server_id,
                format!("WebSocket server-as-parent: {}", self.ws_url),
                sink,
                stream,
                on_disconnect,
                self.state.abortables.clone(),
            ));
        }
        Ok(())
    }

    pub fn disconnect(&mut self) { self.state.disconnect(); }
}
