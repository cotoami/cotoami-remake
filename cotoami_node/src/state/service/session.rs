use core::time::Duration;

use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::debug;

use crate::{
    service::{
        models::{ClientNodeSession, CreateClientNodeSession, Session},
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
            let client_as_parent = input.as_parent();
            let db_role = ds.database_role_of(&input.client.uuid)?;
            match (&db_role, client_as_parent) {
                (Some(DatabaseRole::Parent(_)), true) => (),
                (Some(DatabaseRole::Child(_)), false) => (),
                _ => {
                    return Err(ServiceError::request(
                        "wrong-database-role",
                        "Invalid request of client role.",
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
                server_root_cotonoma: if client_as_parent {
                    None
                } else {
                    ds.root_cotonoma()?
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
