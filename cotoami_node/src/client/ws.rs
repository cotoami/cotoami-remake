//! WebSocket client of Node API Service.

use std::sync::Arc;

use anyhow::{bail, Result};
use cotoami_db::ChildNode;
use futures::{Sink, StreamExt};
use parking_lot::Mutex;
use tokio::sync::{mpsc, mpsc::Receiver};
use tokio_tungstenite::{
    connect_async, tungstenite,
    tungstenite::{client::IntoClientRequest, handshake::client::Request},
};
use tokio_util::sync::PollSender;
use tracing::{debug, info};
use url::Url;

use crate::{
    client::{retry::RetryState, ClientState, ConnectionState, HttpClient},
    event::remote::{
        tungstenite::{communicate_with_operator, communicate_with_parent},
        EventLoopError,
    },
    service::models::NotConnected,
};

#[derive(derive_more::Debug, Clone)]
pub struct WebSocketClient {
    state: Arc<ClientState>,
    ws_request: Request,
    #[debug(skip)]
    reconnecting: Arc<Mutex<Option<RetryState>>>,
}

impl WebSocketClient {
    pub async fn new(state: ClientState, http_client: &HttpClient) -> Result<Self> {
        Ok(Self {
            state: Arc::new(state),
            ws_request: http_client.ws_request()?,
            reconnecting: Arc::new(Mutex::new(None)),
        })
    }

    pub fn as_child(&self) -> Option<&ChildNode> { self.state.as_child() }

    pub fn not_connected(&self) -> Option<NotConnected> { self.state.not_connected() }

    pub async fn connect(&mut self) -> Result<()> {
        if self.state.has_running_tasks() {
            bail!("Already connected");
        }

        self.reconnecting_delay().await?;

        let (tx_on_disconnect, rx_on_disconnect) = mpsc::channel::<Option<EventLoopError>>(1);
        self.handle_disconnection(rx_on_disconnect);

        if let Err(e) = self.do_connect(PollSender::new(tx_on_disconnect)).await {
            if self.reconnecting.lock().is_some() {
                match e {
                    tungstenite::error::Error::Io(e) => {
                        debug!("Continue reconnecting after: {e:?}");
                        Box::pin(self.connect()).await?;
                    }
                    _ => {
                        self.reconnecting.lock().take();
                        bail!("Abort reconnecting due to: {e:?}");
                    }
                }
            }
        }

        Ok(())
    }

    async fn reconnecting_delay(&self) -> Result<()> {
        if let Some(ref mut reconnecting) = *self.reconnecting.lock() {
            if let Some(delay) = reconnecting.next_delay() {
                debug!(
                    "{}. Waiting {delay:?} to reconnect...",
                    reconnecting.last_number()
                );
                tokio::time::sleep(delay).await;
            } else {
                self.reconnecting.lock().take();
                bail!(
                    "Failed to reconnect after {} retries.",
                    reconnecting.last_number()
                );
            }
        }
        Ok(())
    }

    async fn do_connect<S>(&mut self, on_disconnect: S) -> Result<(), tungstenite::error::Error>
    where
        S: Sink<Option<EventLoopError>> + Unpin + Clone + Send + 'static,
    {
        let (ws_stream, _) = connect_async(self.ws_request.clone()).await?;
        info!("WebSocket connection opened: {}", self.ws_request.uri());
        self.state.change_conn_state(ConnectionState::Connected);

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
                format!("WebSocket server-as-parent: {}", self.ws_request.uri()),
                sink,
                stream,
                on_disconnect,
                self.state.abortables.clone(),
            ));
        }
        Ok(())
    }

    fn handle_disconnection(&self, mut receiver: Receiver<Option<EventLoopError>>) {
        tokio::spawn({
            let mut this = self.clone();
            async move {
                while let Some(e) = receiver.recv().await {
                    info!("Disconnected: {e:?}");
                    this.state
                        .change_conn_state(ConnectionState::Disconnected(e));
                    this.reconnecting.lock().replace(RetryState::default());
                    this.connect().await.unwrap();
                }
            }
        });
    }

    pub fn disconnect(&mut self) -> bool { self.state.disconnect() }
}

impl HttpClient {
    pub fn ws_request(&self) -> Result<Request> {
        let ws_url = Url::parse(&self.ws_url_prefix())?.join("/api/ws")?;
        let mut request = ws_url.into_client_request()?;
        request.headers_mut().extend(self.read_headers().clone());
        Ok(request)
    }

    fn ws_url_prefix(&self) -> String {
        // Convert the protocol scheme:
        //   http:// -> ws://
        //   https:// -> wss://
        if self.url_prefix().scheme().starts_with("http") {
            self.url_prefix().as_str().replacen("http", "ws", 1)
        } else {
            unreachable!("url_prefix should start with 'http'.");
        }
    }
}
