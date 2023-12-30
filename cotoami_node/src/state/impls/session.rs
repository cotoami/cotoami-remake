use core::time::Duration;

use anyhow::{bail, Result};
use tokio::task::spawn_blocking;
use tracing::debug;

use crate::{
    service::models::{ClientNodeSession, CreateClientNodeSession, Session},
    state::{error::NodeError, NodeState},
};

impl NodeState {
    pub(crate) async fn create_client_node_session(
        &self,
        input: CreateClientNodeSession,
    ) -> Result<ClientNodeSession> {
        let db = self.db().clone();
        let change_pubsub = self.pubsub().local_changes().clone();
        let session_seconds = self.config().session_seconds();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;

            // Authenticate and start session
            let client = ds.start_client_node_session(
                &input.client.uuid,
                &input.password, // validated to be Some
                Duration::from_secs(session_seconds),
            )?;
            debug!("Client session started: {}", client.node_id);

            // Check database role
            let client_as_parent = input.as_parent.unwrap_or(false);
            if client_as_parent != db.globals().is_parent(&client.node_id) {
                ds.clear_client_node_session(&client.node_id)?;
                bail!(NodeError::WrongDatabaseRole);
            }

            // Change password
            if let Some(new_password) = input.new_password {
                ds.change_client_node_password(&client.node_id, &new_password)?;
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
            })
        })
        .await?
    }
}
