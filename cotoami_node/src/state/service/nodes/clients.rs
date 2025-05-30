use std::sync::Arc;

use anyhow::{bail, Result};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::debug;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{AddClient, ClientAdded, EditClient, Pagination},
        ServiceError,
    },
    state::NodeState,
};

const DEFAULT_RECENT_PAGE_SIZE: i64 = 50;

impl NodeState {
    pub async fn client_node(
        &self,
        id: Id<Node>,
        operator: Arc<Operator>,
    ) -> Result<ClientNode, ServiceError> {
        self.get(move |ds| ds.try_get_client_node(&id, &operator))
            .await
    }

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

        // Generate password if not given
        let password = if let Some(ref password) = input.password {
            password.clone()
        } else {
            cotoami_db::generate_secret(None)
        };

        // Register the node as a client
        spawn_blocking({
            let state = self.clone();
            move || {
                let ds = state.db().new_session()?;
                let (client, node, role) = ds.register_client_node(
                    &input.id.unwrap_or_else(|| unreachable!()),
                    &password,
                    input.into_new_database_role(),
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

    pub async fn reset_client_password(
        &self,
        node_id: Id<Node>,
        operator: Arc<Operator>,
    ) -> Result<String, ServiceError> {
        operator.requires_to_be_owner()?;
        let new_password = spawn_blocking({
            let state = self.clone();
            move || {
                let new_password = cotoami_db::generate_secret(None);
                let ds = state.db().new_session()?;
                ds.change_client_node_password(&node_id, &new_password)?;
                Ok::<_, anyhow::Error>(new_password)
            }
        })
        .await??;
        self.client_conns().disconnect(&node_id);
        Ok(new_password)
    }

    pub async fn edit_client(
        &self,
        node_id: Id<Node>,
        values: EditClient,
        operator: Arc<Operator>,
    ) -> Result<ClientNode, ServiceError> {
        if let Err(errors) = values.validate() {
            return errors.into_result();
        }

        // Set disabled
        if let Some(disabled) = values.disabled {
            self.set_client_disabled(node_id, disabled, operator.clone())
                .await?;
        }

        self.client_node(node_id, operator).await
    }

    async fn set_client_disabled(
        &self,
        node_id: Id<Node>,
        disabled: bool,
        operator: Arc<Operator>,
    ) -> Result<ClientNode, ServiceError> {
        if disabled {
            self.client_conns().disconnect(&node_id);
        }
        spawn_blocking({
            let db = self.db().clone();
            move || {
                let role = db
                    .new_session()?
                    .set_network_disabled(&node_id, disabled, &operator)?;
                let NetworkRole::Client(client) = role else {
                    bail!("Unexpected node role: {role:?}");
                };
                Ok(client)
            }
        })
        .await?
        .map_err(ServiceError::from)
    }
}
