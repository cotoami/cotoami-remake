use core::time::Duration;

use anyhow::{bail, Result};
use tokio::task::spawn_blocking;

use crate::{
    service::{ClientNodeSession, CreateClientNodeSession, Session},
    state::{error::NodeError, NodeState},
};

impl NodeState {
    pub async fn create_client_node_session(
        &self,
        input: CreateClientNodeSession,
    ) -> Result<ClientNodeSession> {
        let db = self.db().clone();
        let change_pubsub = self.pubsub().local_change.clone();
        let session_seconds = self.config().session_seconds();
        spawn_blocking(move || {
            let mut db = db.new_session()?;

            // Authenticate and start session
            let client = db.start_client_node_session(
                &input.client.uuid,
                &input.password, // validated to be Some
                Duration::from_secs(session_seconds),
            )?;

            // Check database role
            if input.as_parent.unwrap_or(false) && !db.is_parent(&client.node_id) {
                db.clear_client_node_session(&client.node_id)?;
                bail!(NodeError::WrongDatabaseRole);
            }

            // Change password
            if let Some(new_password) = input.new_password {
                db.change_client_node_password(&client.node_id, &new_password)?;
            }

            // Import the client node
            if let Some((_, changelog)) = db.import_node(&input.client)? {
                change_pubsub.publish(changelog, None);
            }

            Ok(ClientNodeSession {
                session: Session {
                    token: client.session_token.unwrap(),
                    expires_at: client.session_expires_at.unwrap(),
                },
                server: db.local_node()?,
            })
        })
        .await?
    }
}
