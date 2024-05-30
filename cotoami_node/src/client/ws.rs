//! WebSocket client of Node API Service.

use std::sync::Arc;

use anyhow::{bail, Result};
use cotoami_db::{Id, Node};
use futures::{Sink, StreamExt};
use tokio::sync::mpsc;
use tokio_tungstenite::{
    connect_async,
    tungstenite::{
        client::IntoClientRequest,
        handshake::client::Request,
        http::{HeaderName, HeaderValue},
    },
};
use tokio_util::sync::PollSender;
use tracing::info;
use url::Url;

use crate::{
    client::{ClientState, ConnectionState, HttpClient},
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
    ws_request: Request,
}

impl WebSocketClient {
    pub async fn new(
        server_id: Id<Node>,
        http_client: &HttpClient,
        node_state: NodeState,
    ) -> Result<Self> {
        let state = ClientState::new(server_id, node_state).await?;
        Ok(Self {
            state: Arc::new(state),
            ws_request: http_client.ws_request()?,
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
                    info!("on_disconnect: {err:?}");
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
        let (ws_stream, _) = connect_async(self.ws_request.clone()).await?;
        info!("WebSocket connection opened: {}", self.ws_request.uri());
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
                format!("WebSocket server-as-parent: {}", self.ws_request.uri()),
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

impl HttpClient {
    pub fn ws_request(&self) -> Result<Request> {
        let ws_url = Url::parse(&self.ws_url_prefix())?.join("/api/ws")?;
        let mut request = ws_url.into_client_request()?;
        {
            // FIXME: incompatible `http` versions between reqwest and tungstenite
            // make me write this silly conversion.
            // Waiting for the following issue to be fixed:
            // https://github.com/seanmonstar/reqwest/issues/2039
            let headers = request.headers_mut();
            for (name, value) in self.all_headers().into_iter() {
                let value = HeaderValue::from_bytes(value.as_bytes())?;
                if let Some(name) = name {
                    let name = HeaderName::from_bytes(name.as_ref())?;
                    headers.append(name, value);
                } else {
                    // Just ignore the `None` case
                }
            }
        }
        Ok(request)
    }

    fn ws_url_prefix(&self) -> String {
        if self.url_prefix().scheme().starts_with("http") {
            self.url_prefix().as_str().replacen("http", "ws", 1)
        } else {
            unreachable!("url_prefix should start with 'http'.");
        }
    }
}
