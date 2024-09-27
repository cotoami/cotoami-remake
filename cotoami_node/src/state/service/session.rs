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
        input: CreateClientNodeSession,
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
                &input.password,
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
                session: Session {
                    token: client.session_token.unwrap(),
                    expires_at: client.session_expires_at.unwrap(),
                },
                server: ds.local_node()?,
                server_root_cotonoma: match client_role {
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
}
