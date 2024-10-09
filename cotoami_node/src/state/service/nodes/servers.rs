use std::sync::Arc;

use anyhow::{bail, Result};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::{debug, info};
use validator::Validate;

use crate::{
    client::HttpClient,
    service::{
        error::IntoServiceResult,
        models::{ClientNodeSession, LogIntoServer, Server, UpdateServer},
        RemoteNodeServiceExt, ServiceError,
    },
    state::{NodeState, ServerConnection},
};

impl NodeState {
    pub async fn server_node(
        &self,
        id: Id<Node>,
        operator: Arc<Operator>,
    ) -> Result<ServerNode, ServiceError> {
        self.get(move |ds| ds.try_get_server_node(&id, &operator))
            .await
    }

    pub async fn all_servers(&self, operator: Arc<Operator>) -> Result<Vec<Server>, ServiceError> {
        let this = self.clone();
        self.get(move |ds| {
            let server_nodes = ds.all_server_nodes(&operator)?;
            let node_ids: Vec<Id<Node>> = server_nodes.iter().map(|(_, node)| node.uuid).collect();
            let mut roles = ds.database_roles_of(&node_ids)?;
            let servers = server_nodes
                .into_iter()
                .map(|(server, _)| {
                    let node_id = server.node_id;
                    let conn = this
                        .server_conns()
                        .get(&node_id)
                        .unwrap_or_else(|| unreachable!());
                    Server::new(
                        server,
                        roles.remove(&node_id),
                        conn.not_connected(),
                        conn.local_as_child(),
                    )
                })
                .collect();
            Ok(servers)
        })
        .await
    }

    pub async fn log_into_server(
        &self,
        input: LogIntoServer,
    ) -> Result<(ClientNodeSession, HttpClient), ServiceError> {
        if let Err(errors) = input.validate() {
            return errors.into_result();
        }

        let url_prefix = input.url_prefix.clone().unwrap_or_else(|| unreachable!());
        let session_request = input.into_session_request(self.local_node().await?)?;

        let mut http_client = HttpClient::new(&url_prefix)?;
        let session = http_client
            .create_client_node_session(session_request)
            .await?;
        info!("Successfully logged in to {}", http_client.url_prefix());

        Ok((session, http_client))
    }

    pub async fn add_server(
        &self,
        input: LogIntoServer,
        operator: Arc<Operator>,
    ) -> Result<Server, ServiceError> {
        if let Err(errors) = input.validate() {
            return errors.into_result();
        }

        // Clone the password before moving the `input` to `log_into_server`
        let password = input.password.clone().unwrap_or_else(|| unreachable!());

        // Log into the server to create a session
        // TODO: change the password on adding the node
        let (client_session, http_client) = self.log_into_server(input).await?;
        let server_id = client_session.server.uuid;

        // Register the server node
        let (server, server_node, server_role, local_as_child) = spawn_blocking({
            let state = self.clone();
            let operator = operator.clone();
            let url_prefix = http_client.url_prefix().to_string();
            move || {
                let mut ds = state.db().new_session()?;

                // Import the server node data, which is necessary for registering a ServerNode
                if let Some((_, changelog)) = ds.import_node(&client_session.server)? {
                    state.pubsub().publish_change(changelog);
                }

                // Register a ServerNode
                let server_role = if client_session.as_child.is_some() {
                    NewDatabaseRole::Parent
                } else {
                    // TODO: want to make it configurable later
                    NewDatabaseRole::Child {
                        as_owner: false,
                        can_edit_links: false,
                    }
                };
                let (_, server_role) =
                    ds.register_server_node(&server_id, &url_prefix, server_role, &operator)?;

                // Save the password in the ServerNode for auto-login
                let master_password = state.config().try_get_owner_password()?;
                let server =
                    ds.save_server_password(&server_id, &password, master_password, &operator)?;

                // Results
                let node = ds.node(&server_id)?.unwrap_or_else(|| unreachable!());
                Ok::<_, ServiceError>((server, node, server_role, client_session.as_child))
            }
        })
        .await??;
        info!("ServerNode [{}] registered.", server_node.name);

        // Create a ServerConnection
        let server_conn = ServerConnection::new(server.clone(), self.clone());
        server_conn
            .start_event_loop(http_client.clone(), local_as_child)
            .await?;
        self.server_conns().put(server_id, server_conn.clone());

        // Return a Server as a response
        let server = Server::new(
            server,
            Some(server_role),
            server_conn.not_connected(),
            server_conn.local_as_child(),
        );
        Ok(server)
    }

    pub async fn update_server(
        &self,
        node_id: Id<Node>,
        values: UpdateServer,
        operator: Arc<Operator>,
    ) -> Result<ServerNode, ServiceError> {
        if let Err(errors) = values.validate() {
            return errors.into_result();
        }
        if !self.server_conns().contains(&node_id) {
            return Err(ServiceError::NotFound(Some(format!(
                "Server node [{node_id}] not found."
            ))));
        }

        // TODO: Set url_prefix

        // Set disabled
        if let Some(disabled) = values.disabled {
            self.set_server_disabled(node_id, disabled, operator.clone())
                .await?;
        }

        // Recreate a connection with the updated [ServerNode].
        let server = self.server_node(node_id, operator).await?;
        let conn = ServerConnection::new(server.clone(), self.clone());
        conn.connect().await;
        self.server_conns().put(node_id, conn);

        Ok(server)
    }

    async fn set_server_disabled(
        &self,
        node_id: Id<Node>,
        disabled: bool,
        operator: Arc<Operator>,
    ) -> Result<ServerNode> {
        debug!("Updating a server node [{node_id}] to be disabled = {disabled}");
        let server = spawn_blocking({
            let db = self.db().clone();
            move || {
                let role = db
                    .new_session()?
                    .set_network_disabled(&node_id, disabled, &operator)?;
                let NetworkRole::Server(server) = role else {
                    bail!("Unexpected node role: {role:?}");
                };
                Ok(server)
            }
        })
        .await??;

        if disabled {
            // Just to publish a connection state change.
            self.server_conns().try_get(&node_id)?.disable();
        }
        Ok(server)
    }
}
