use core::time::Duration;

use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::debug;

use crate::{
    service::{
        models::{ClientNodeSession, CreateClientNodeSession, NodeRole, Session},
        ServiceError,
    },
    state::NodeState,
};

impl NodeState {
    pub async fn create_client_node_session(
        &self,
        mut input: CreateClientNodeSession,
    ) -> Result<ClientNodeSession, ServiceError> {
        let (local, local_node) = self.local_node_pair().await?;
        match (input.password.take(), local.enable_anonymous_read) {
            (Some(password), _) => self.do_create_session(input, password, local_node).await,
            (None, true) => self.handle_anonymous(input, local_node).await,
            (None, false) => return Err(ServiceError::Unauthorized),
        }
    }

    async fn do_create_session(
        &self,
        input: CreateClientNodeSession,
        password: String,
        local_node: Node,
    ) -> Result<ClientNodeSession, ServiceError> {
        let db = self.db().clone();
        let change_pubsub = self.pubsub().changes().clone();
        let session_seconds = self.config().session_seconds();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;

            // Check database role
            let client_role = input.client_role();
            let db_role = ds.database_role_of(&input.client.uuid)?;
            match (client_role, &db_role) {
                (NodeRole::Parent, Some(DatabaseRole::Parent(_))) => (),
                (NodeRole::Child, Some(DatabaseRole::Child(_))) => (),
                (_, None) => {
                    return Err(ServiceError::Unauthorized);
                }
                _ => {
                    return Err(ServiceError::request(
                        "invalid-requested-role",
                        format!(
                            "The requested role ({client_role:?}) doesn't \
                            match to the actual role of the client node ({}).",
                            input.client.name
                        ),
                    ));
                }
            }

            // Authenticate and start session
            let client = ds.start_client_node_session(
                &input.client.uuid,
                &password,
                Duration::from_secs(session_seconds),
            )?;
            debug!("Client session started: {}", client.node_id);

            // Change password
            if let Some(new_password) = input.new_password {
                ds.change_client_node_password(&client.node_id, &new_password)?;
                debug!("Password changed.");
            }

            // Import the client node
            if let Some((_, changelog)) = ds.import_node(&input.client)? {
                change_pubsub.publish(changelog, None);
                debug!("Client node imported: {}", client.node_id);
            }

            Ok(ClientNodeSession {
                session: Some(Session {
                    token: client.session_token.unwrap(),
                    expires_at: client.session_expires_at.unwrap(),
                }),
                server: local_node,
                server_root: match client_role {
                    NodeRole::Parent => None,
                    NodeRole::Child => ds.local_node_root()?,
                },
                as_child: if let Some(DatabaseRole::Child(child)) = db_role {
                    Some(child)
                } else {
                    None
                },
            })
        })
        .await?
    }

    async fn handle_anonymous(
        &self,
        input: CreateClientNodeSession,
        local_node: Node,
    ) -> Result<ClientNodeSession, ServiceError> {
        if let NodeRole::Parent = input.client_role() {
            return Err(ServiceError::request(
                "invalid-requested-role",
                "Anonymous clients are not allowed to be a parent.",
            ));
        }

        Ok(ClientNodeSession {
            session: None,
            server: local_node,
            server_root: self.local_node_root().await?,
            as_child: None,
        })
    }
}
