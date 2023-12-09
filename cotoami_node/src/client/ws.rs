//! WebSocket client of Node API Service.

use anyhow::Result;
use tokio_tungstenite::{connect_async, WebSocketStream};
use url::Url;

pub struct WebSocketClient {
    url_prefix: String,
}

impl WebSocketClient {
    pub async fn new(url_prefix: String) -> Result<Self> {
        let ws_url = Url::parse(&url_prefix)?.join("/api/ws")?;
        let (ws_stream, _) = connect_async(ws_url).await?;
        Ok(Self { url_prefix })
    }
}
