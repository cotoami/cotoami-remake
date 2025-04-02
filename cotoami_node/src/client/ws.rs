//! WebSocket client of Node API Service.

use std::sync::Arc;

use anyhow::{anyhow, bail, Result};
use cotoami_db::ChildNode;
use futures::{Sink, StreamExt};
use parking_lot::Mutex;
use tokio::sync::{mpsc, mpsc::Receiver};
use tokio_tungstenite::{
    connect_async, tungstenite,
    tungstenite::{client::IntoClientRequest, handshake::client::Request},
};
use tokio_util::sync::PollSender;
use tracing::{debug, error, info};
use url::Url;

use crate::{
    client::{retry::RetryState, ClientState, ConnectionState, HttpClient},
    event::remote::{
        tungstenite::{communicate_with_operator, communicate_with_parent},
        CommunicationError,
    },
    service::models::NotConnected,
};

#[derive(derive_more::Debug, Clone)]
pub struct WebSocketClient {
    state: ClientState,
    http_client: HttpClient,
    ws_request: Request,
    #[debug(skip)]
    reconnecting: Arc<Mutex<Option<RetryState>>>,
}

impl WebSocketClient {
    pub async fn new(state: ClientState, http_client: HttpClient) -> Result<Self> {
        let ws_request = http_client.ws_request()?;
        Ok(Self {
            state,
            http_client,
            ws_request,
            reconnecting: Arc::new(Mutex::new(None)),
        })
    }

    pub fn child_privileges(&self) -> Option<ChildNode> { self.state.child_privileges() }

    pub fn not_connected(&self) -> Option<NotConnected> { self.state.not_connected() }

    pub async fn connect(&mut self) -> Result<()> {
        if self.state.has_running_tasks() {
            bail!("Already connected");
        }

        let (tx_abort, rx_abort) = mpsc::channel::<Option<CommunicationError>>(1);
        self.run_handler_on_abort(rx_abort);

        if let Err(e) = self.do_connect(PollSender::new(tx_abort)).await {
            if self.reconnecting.lock().is_some() {
                match e {
                    tungstenite::error::Error::Io(e) => {
                        debug!("Continue reconnecting after: {e:?}");
                        self.run_reconnecting_task();
                    }
                    _ => {
                        debug!("Abort reconnecting due to: {e:?}");
                        self.reconnecting.lock().take();
                        self.state
                            .change_conn_state(ConnectionState::connection_error(e.into()));
                        bail!("Abort reconnecting");
                    }
                }
            } else {
                return Err(anyhow!(e));
            }
        }

        Ok(())
    }

    async fn do_connect<S>(&mut self, on_abort: S) -> Result<(), tungstenite::error::Error>
    where
        S: Sink<Option<CommunicationError>> + Unpin + Clone + Send + 'static,
    {
        let (ws_stream, _) = connect_async(self.ws_request.clone()).await?;
        self.state.change_conn_state(ConnectionState::Connected);

        if self.reconnecting.lock().is_some() {
            info!("WebSocket reconnected: {}", self.ws_request.uri());
            self.reconnecting.lock().take();
        } else {
            info!("WebSocket connection opened: {}", self.ws_request.uri());
        }

        let (sink, stream) = ws_stream.split();
        if let Some(opr) = self.state.server_as_operator.as_ref() {
            tokio::spawn(communicate_with_operator(
                self.state.node_state.clone(),
                opr.clone(),
                sink,
                stream,
                on_abort,
                self.state.abortables.clone(),
            ));
        } else {
            tokio::spawn(communicate_with_parent(
                self.state.node_state.clone(),
                self.state.server_id,
                format!("WebSocket server-as-parent: {}", self.ws_request.uri()),
                sink,
                stream,
                on_abort,
                self.state.abortables.clone(),
            ));
        }
        Ok(())
    }

    fn run_handler_on_abort(&self, mut receiver: Receiver<Option<CommunicationError>>) {
        tokio::spawn({
            let this = self.clone();
            async move {
                if let Some(e) = receiver.recv().await {
                    info!("Event loop aborted: {e:?}");
                    this.state.abortables.abort_all();
                    if let Some(CommunicationError::Connection(e)) = e {
                        debug!("Start reconnecting...");
                        this.reconnecting.lock().replace(RetryState::default());
                        this.state
                            .change_conn_state(ConnectionState::Connecting(Some(e)));
                        this.run_reconnecting_task();
                    } else {
                        this.state
                            .change_conn_state(ConnectionState::Disconnected(e));
                    }
                }
            }
        });
    }

    fn run_reconnecting_task(&self) {
        tokio::spawn({
            let mut this = self.clone();
            async move {
                if let Err(e) = this.reconnecting_delay().await {
                    this.state
                        .change_conn_state(ConnectionState::connection_error(e));
                } else {
                    this.connect().await.ok();
                }
            }
        });
    }

    async fn reconnecting_delay(&self) -> Result<()> {
        if self.reconnecting.lock().is_none() {
            return Ok(()); // Not reconnecting
        }

        let (next_delay, number) = {
            let mut lock = self.reconnecting.lock();
            let state = lock.as_mut().unwrap();
            (state.next_delay(), state.last_number())
        };

        if let Some(delay) = next_delay {
            debug!("({}) waiting {delay:?} to reconnect...", number);
            tokio::time::sleep(delay).await;
        } else {
            self.reconnecting.lock().take();
            bail!("Failed to reconnect after {} retries.", number);
        }
        Ok(())
    }

    pub async fn disconnect(&self) -> bool {
        self.reconnecting.lock().take(); // cancel reconnecting
        if let Err(e) = self.http_client.delete_session().await {
            error!("Failed to delete the session: {e:?}")
        }
        self.state.disconnect()
    }
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
