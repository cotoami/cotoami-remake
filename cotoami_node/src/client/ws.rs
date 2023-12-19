//! WebSocket client of Node API Service.

use std::sync::Arc;

use anyhow::Result;
use cotoami_db::{Id, Node};
use futures::StreamExt;
use tokio_tungstenite::connect_async;
use url::Url;

use crate::{
    client::{ClientState, ConnectionState},
    event::tungstenite::{handle_operator, handle_parent},
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

    pub async fn connect(&mut self) {
        if self.state.has_running_tasks() {
            return;
        }
        match connect_async(&self.ws_url).await {
            Err(e) => {
                self.state
                    .set_conn_state(ConnectionState::init_failed(e.into()));
            }
            Ok((ws_stream, _)) => {
                let (sink, stream) = ws_stream.split();
                if let Some(opr) = self.state.server_as_operator.as_ref() {
                    handle_operator(
                        opr.clone(),
                        sink,
                        stream,
                        self.state.node_state.clone(),
                        self.state.abortables.lock().as_mut(),
                    )
                    .await;
                } else {
                    handle_parent(
                        self.state.server_id,
                        &format!("WebSocket server-as-parent: {}", self.ws_url),
                        sink,
                        stream,
                        &self.state.node_state,
                        self.state.abortables.lock().as_mut(),
                    )
                    .await;
                }
            }
        }
    }

    pub fn disconnect(&mut self) { self.state.disconnect(); }
}
