use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::debug;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{AddClient, ClientAdded, NodeRole, Pagination},
        ServiceError,
    },
    state::NodeState,
};

const DEFAULT_RECENT_PAGE_SIZE: i64 = 20;

impl NodeState {
    pub async fn recent_clients(
        &self,
        pagination: Pagination,
        operator: Arc<Operator>,
    ) -> Result<Page<ClientNode>, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return errors.into_result();
        }
        self.get(move |ds| {
            let page = ds
                .recent_client_nodes(
                    pagination.page_size.unwrap_or(DEFAULT_RECENT_PAGE_SIZE),
                    pagination.page,
                    &operator,
                )?
                .map(|(client, _)| client)
                .into();
            Ok(page)
        })
        .await
    }

    pub async fn add_client(
        &self,
        input: AddClient,
        operator: Arc<Operator>,
    ) -> Result<ClientAdded, ServiceError> {
        if let Err(errors) = input.validate() {
            return errors.into_result();
        }

        // Inputs
        let role = match input.client_role() {
            NodeRole::Parent => NewDatabaseRole::Parent,
            NodeRole::Child => NewDatabaseRole::Child {
                as_owner: input.as_owner(),
                can_edit_links: input.can_edit_links(),
            },
        };
        let password = if let Some(password) = input.password {
            password
        } else {
            cotoami_db::generate_secret(None)
        };

        // Register the node as a client
        spawn_blocking({
            let state = self.clone();
            move || {
                let ds = state.db().new_session()?;
                let (client, node, role) = ds.register_client_node(
                    input.id.unwrap_or_else(|| unreachable!()),
                    &password,
                    role,
                    &operator,
                )?;
                debug!("Client node ({}) registered as a {}", client.node_id, role);
                Ok(ClientAdded {
                    password,
                    client,
                    node,
                })
            }
        })
        .await?
    }
}
