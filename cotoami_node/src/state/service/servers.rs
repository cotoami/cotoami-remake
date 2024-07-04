use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::{debug, info};
use validator::Validate;

use crate::{
    client::HttpClient,
    service::{
        error::IntoServiceResult,
        models::{ClientNodeSession, ConnectServerNode, Server, UpdateServerNode},
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
                    Server::new(server, conn.not_connected(), roles.remove(&node_id))
                })
                .collect();
            Ok(servers)
        })
        .await
    }

    pub async fn connect_server_node(
        &self,
        input: ConnectServerNode,
    ) -> Result<(ClientNodeSession, HttpClient), ServiceError> {
        if let Err(errors) = input.validate() {
            return ("connect_server_node", errors).into_result();
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

    pub async fn add_server_node(
        &self,
        input: ConnectServerNode,
        operator: Arc<Operator>,
    ) -> Result<Server, ServiceError> {
        if let Err(errors) = input.validate() {
            return ("add_server_node", errors).into_result();
        }

        // Save the password before moving the `input`
        let password = input.password.clone().unwrap_or_else(|| unreachable!());

        // TODO: change the password on adding the node
        let (client_session, http_client) = self.connect_server_node(input).await?;
        let server_id = client_session.server.uuid;

        // Register the server node
        let (server, server_node, server_db_role) = spawn_blocking({
            let state = self.clone();
            let operator = operator.clone();
            let url_prefix = http_client.url_prefix().to_string();
            move || {
                let mut ds = state.db().new_session()?;

                // Import the server node data, which is required for registering a [ServerNode]
                if let Some((_, changelog)) = ds.import_node(&client_session.server)? {
                    state.pubsub().publish_change(changelog);
                }

                // Database role
                let server_db_role = if client_session.as_child.is_some() {
                    NewDatabaseRole::Parent
                } else {
                    NewDatabaseRole::Child {
                        as_owner: false,
                        can_edit_links: false,
                    }
                };

                // Register a [ServerNode] and save the password into it
                let owner_password = state.config().try_get_owner_password()?;
                let (_, server_db_role) =
                    ds.register_server_node(&server_id, &url_prefix, server_db_role, &operator)?;
                let server =
                    ds.save_server_password(&server_id, &password, owner_password, &operator)?;

                // Get the imported node data
                let node = ds.node(&server_id)?.unwrap_or_else(|| unreachable!());
                Ok::<_, ServiceError>((server, node, server_db_role))
            }
        })
        .await??;
        info!("ServerNode [{}] registered.", server_node.name);

        // Create a ServerConnection
        let server_conn = ServerConnection::new(server.clone(), self.clone());
        server_conn.start_event_loop(http_client.clone()).await?;
        self.server_conns().put(server_id, server_conn.clone());

        // Return a Server as a response
        let server = Server::new(server, server_conn.not_connected(), Some(server_db_role));
        Ok(server)
    }

    pub async fn update_server_node(
        &self,
        node_id: Id<Node>,
        values: UpdateServerNode,
        operator: Arc<Operator>,
    ) -> Result<(), ServiceError> {
        if let Err(errors) = values.validate() {
            return ("update_server_node", errors).into_result();
        }
        if !self.server_conns().contains(&node_id) {
            return Err(ServiceError::NotFound(Some(format!(
                "Server node [{node_id}] not found."
            ))));
        }

        // TODO: Set url_prefix

        // Set disabled
        if let Some(disabled) = values.disabled {
            debug!("Updating a server node [{node_id}] to be disabled = {disabled}");
            spawn_blocking({
                let db = self.db().clone();
                let operator = operator.clone();
                move || {
                    db.new_session()?
                        .set_network_disabled(&node_id, disabled, &operator)
                }
            })
            .await??;

            if disabled {
                // Just to publish a connection state change.
                self.server_conns().try_get(&node_id)?.disable();
            }
        }

        // Recreate a connection with the updated [ServerNode].
        let server = self.server_node(node_id, operator).await?;
        let conn = ServerConnection::new(server, self.clone());
        conn.connect().await;
        self.server_conns().put(node_id, conn);

        Ok(())
    }
}
