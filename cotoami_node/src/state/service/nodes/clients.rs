use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::debug;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{AddClientNode, ClientNodeAdded, NodeRole},
        ServiceError,
    },
    state::NodeState,
};

impl NodeState {
    pub async fn add_client_node(
        &self,
        input: AddClientNode,
        operator: Arc<Operator>,
    ) -> Result<ClientNodeAdded, ServiceError> {
        if let Err(errors) = input.validate() {
            return ("add_client_node", errors).into_result();
        }

        // Inputs
        let password = cotoami_db::generate_secret(None);
        let role = match input.client_role() {
            NodeRole::Parent => NewDatabaseRole::Parent,
            NodeRole::Child => NewDatabaseRole::Child {
                as_owner: input.as_owner(),
                can_edit_links: input.can_edit_links(),
            },
        };

        // Register the node as a client
        spawn_blocking({
            let state = self.clone();
            move || {
                let ds = state.db().new_session()?;
                let (client, role) = ds.register_client_node(
                    input.id.unwrap_or_else(|| unreachable!()),
                    &password,
                    role,
                    &operator,
                )?;
                debug!("Client node ({}) registered as a {}", client.node_id, role);
                Ok(ClientNodeAdded { password })
            }
        })
        .await?
    }
}
