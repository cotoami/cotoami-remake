use std::{collections::HashMap, sync::Arc};

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use parking_lot::RwLock;
use tracing::{debug, info};

use crate::{
    api::session::{CreateClientNodeSession, Session},
    client::{HttpClient, SseClient, SseClientError, SseClientState},
    service::{NodeServiceExt, RemoteNodeServiceExt},
    ChangePubsub,
};

pub enum ServerConnection {
    Disabled,
    InitFailed(anyhow::Error),
    Connected {
        session: Session,
        http_client: HttpClient,
        sse_client_state: Arc<RwLock<SseClientState>>,
    },
}

impl ServerConnection {
    pub fn new(session: Session, http_client: HttpClient, mut sse_client: SseClient) -> Self {
        let server_conn = ServerConnection::Connected {
            session,
            http_client,
            sse_client_state: sse_client.state(),
        };
        tokio::spawn(async move {
            sse_client.start().await;
        });
        server_conn
    }

    pub async fn connect(
        server_node: &ServerNode,
        local_node: Node,
        owner_password: &str,
        db: &Arc<Database>,
        change_pubsub: &Arc<ChangePubsub>,
    ) -> Self {
        match Self::try_connect(server_node, local_node, owner_password, db, change_pubsub).await {
            Ok(conn) => conn,
            Err(err) => {
                debug!("Failed to initialize a server connection: {:?}", err);
                ServerConnection::InitFailed(err)
            }
        }
    }

    async fn try_connect(
        server_node: &ServerNode,
        local_node: Node,
        owner_password: &str,
        db: &Arc<Database>,
        change_pubsub: &Arc<ChangePubsub>,
    ) -> Result<Self> {
        let is_server_parent = db.new_session()?.is_parent(&server_node.node_id);
        let mut http_client = HttpClient::new(server_node.url_prefix.clone())?;

        // Attempt to log into the server node
        let password = server_node
            .password(owner_password)?
            .ok_or(anyhow!("Server password is missing."))?;
        let client_session = http_client
            .create_client_node_session(CreateClientNodeSession {
                password,
                new_password: None,
                client: local_node,
                as_parent: Some(is_server_parent),
            })
            .await?;
        info!("Successfully logged in to {}", http_client.url_prefix());

        // Import changes from the parent
        if is_server_parent {
            http_client
                .import_changes(server_node.node_id, db, change_pubsub)
                .await?;
        }

        // Create a SSE client
        let sse_client = SseClient::new(
            server_node.node_id,
            http_client.clone(),
            db.clone(),
            change_pubsub.clone(),
        )?;

        Ok(Self::new(client_session.session, http_client, sse_client))
    }

    pub fn disable_sse(&self) {
        if let ServerConnection::Connected {
            sse_client_state, ..
        } = self
        {
            sse_client_state.write().disable();
        }
    }

    pub fn restart_sse_if_possible(&self) -> bool {
        if let ServerConnection::Connected {
            sse_client_state, ..
        } = self
        {
            sse_client_state.write().restart_if_possible()
        } else {
            false
        }
    }

    pub fn not_connected(&self) -> Option<NotConnected> { NotConnected::check_status(self) }
}

#[derive(serde::Serialize)]
#[serde(tag = "reason", content = "details")]
pub enum NotConnected {
    Disabled,
    Connecting(Option<String>),
    InitFailed(String),
    StreamFailed(String),
    EventHandlingFailed(String),
    Unknown,
}

impl NotConnected {
    fn check_status(conn: &ServerConnection) -> Option<Self> {
        match conn {
            ServerConnection::Disabled => Some(NotConnected::Disabled),
            ServerConnection::InitFailed(e) => Some(NotConnected::InitFailed(e.to_string())),
            ServerConnection::Connected {
                sse_client_state, ..
            } => {
                let state = sse_client_state.read();
                if state.is_running() {
                    None // connected
                } else if state.is_disabled() {
                    Some(NotConnected::Disabled)
                } else if state.is_connecting() {
                    let details =
                        if let Some(SseClientError::StreamFailed(e)) = state.error.as_ref() {
                            Some(e.to_string())
                        } else {
                            None
                        };
                    Some(NotConnected::Connecting(details))
                } else if let Some(error) = state.error.as_ref() {
                    match error {
                        SseClientError::StreamFailed(e) => {
                            Some(NotConnected::StreamFailed(e.to_string()))
                        }
                        SseClientError::EventHandlingFailed(e) => {
                            Some(NotConnected::EventHandlingFailed(e.to_string()))
                        }
                    }
                } else {
                    Some(NotConnected::Unknown)
                }
            }
        }
    }
}

pub type ServerConnections = HashMap<Id<Node>, ServerConnection>;
