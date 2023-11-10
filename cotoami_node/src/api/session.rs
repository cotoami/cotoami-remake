use core::time::Duration;
use std::sync::Arc;

use chrono::NaiveDateTime;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use super::error::{ApiError, RequestError};
use crate::ChangePubsub;

#[derive(Clone, serde::Serialize, serde::Deserialize)]
pub struct CreateClientNodeSession {
    pub password: String,
    pub new_password: Option<String>,
    pub client: Node,
    pub as_parent: Option<bool>,
}

#[derive(serde::Serialize, serde::Deserialize)]
pub struct Session {
    pub token: String,
    pub expires_at: NaiveDateTime, // UTC
}

#[derive(serde::Serialize, serde::Deserialize)]
pub struct ClientNodeSession {
    pub session: Session,
    pub server: Node,
}

pub(crate) async fn create_client_node_session(
    input: CreateClientNodeSession,
    session_seconds: u64,
    db: Arc<Database>,
    change_pubsub: Arc<ChangePubsub>,
) -> Result<ClientNodeSession, ApiError> {
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
            return Err(ApiError::Request(RequestError::new("wrong-database-role")));
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
