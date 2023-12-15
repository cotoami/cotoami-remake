//! WebSocket client of Node API Service.

use std::sync::Arc;

use anyhow::Result;
use cotoami_db::{ChangelogEntry, Id, Node, Operator};
use tokio_tungstenite::{connect_async, WebSocketStream};
use url::Url;

#[derive(Clone)]
pub struct WebSocketClient {
    state: Arc<State>,
}

struct State {
    server_id: Id<Node>,
    url_prefix: String,
    ws_url: Url,
}

impl WebSocketClient {
    pub fn new(server_id: Id<Node>, url_prefix: String) -> Result<Self> {
        let ws_url = Url::parse(&url_prefix)?.join("/api/ws")?;
        let state = State {
            server_id,
            url_prefix,
            ws_url,
        };
        Ok(Self {
            state: Arc::new(state),
        })
    }
}
