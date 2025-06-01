//! WebSocket client of Node API Service.

use std::{sync::Arc, time::Duration};

use anyhow::{anyhow, bail, ensure, Result};
use cotoami_db::ChildNode;
use futures::{Sink, StreamExt};
use parking_lot::Mutex;
use reqwest::StatusCode;
use tokio::sync::{mpsc, mpsc::Receiver};
use tokio_tungstenite::{
    connect_async_with_config, tungstenite,
    tungstenite::{
        client::IntoClientRequest, handshake::client::Request, protocol::WebSocketConfig,
    },
};
use tokio_util::sync::PollSender;
use tracing::{debug, info};
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
    const PING_INTERVAL: Duration = Duration::from_secs(30);

    pub async fn new(state: ClientState, http_client: HttpClient) -> Result<Self> {
        let ws_request = http_client.ws_request()?;
        Ok(Self {
            state,
            http_client,
            ws_request,
            reconnecting: Arc::new(Mutex::new(None)),
        })
    }

    fn config(&self) -> WebSocketConfig {
        let node_config = self.state.node_state.read_config();
        // tungstenite's high-level API doesn't seem to fragment messages,
        // so the size of a frame roughly matches that of the message.
        // cf. https://github.com/snapview/tungstenite-rs/issues/303#issuecomment-1245805810
        WebSocketConfig::default()
            .max_message_size(node_config.max_message_size_as_client)
            .max_frame_size(node_config.max_message_size_as_client)
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
                        self.next_attempt_to_reconnect();
                    }
                    tungstenite::error::Error::Http(response)
                        if response.status() == StatusCode::UNAUTHORIZED =>
                    {
                        debug!("Abort reconnecting due to session expiration");
                        self.reconnecting.lock().take();
                        self.state
                            .change_conn_state(ConnectionState::session_expired());
                        bail!("Abort reconnecting");
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
        let (ws_stream, _) =
            connect_async_with_config(self.ws_request.clone(), Some(self.config()), false).await?;
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
                Some(Self::PING_INTERVAL),
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
                Some(Self::PING_INTERVAL),
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

                    // If the error is a connection error, we may try to reconnect.
                    if let Some(CommunicationError::Connection(e)) = e {
                        if let Some(tungstenite::error::Error::Capacity(_)) =
                            e.downcast_ref::<tungstenite::error::Error>()
                        {
                            // ex. The message is bigger than the maximum allowed size.
                            this.state
                                .change_conn_state(ConnectionState::connection_error(e));
                        } else {
                            debug!("Start reconnecting...");
                            this.reconnecting.lock().replace(RetryState::default());
                            this.state
                                .change_conn_state(ConnectionState::Connecting(Some(e)));
                            this.next_attempt_to_reconnect();
                        }
                    } else {
                        this.state
                            .change_conn_state(ConnectionState::Disconnected(e));
                    }
                }
            }
        });
    }

    fn next_attempt_to_reconnect(&self) {
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
        ensure!(
            self.reconnecting.lock().is_some(),
            "reconnecting_delay was called not during reconnecting."
        );

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
            debug!("Failed to delete the session: {e:?}")
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
