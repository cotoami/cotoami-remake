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
        models::{ClientNodeSession, EditServer, LogIntoServer, Server},
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
                        conn.child_privileges(),
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
        let session_request =
            input.into_session_request(self.local_node().await?, self.version().to_owned())?;

        let mut http_client = HttpClient::new(&url_prefix)?;
        let session = http_client
            .create_client_node_session(session_request)
            .await?;
        info!("Successfully logged in to {}", http_client.url_prefix());

        Ok((session, http_client))
    }

    pub async fn add_server(
        &self,
        mut input: LogIntoServer,
        operator: Arc<Operator>,
    ) -> Result<Server, ServiceError> {
        if let Err(errors) = input.validate() {
            return errors.into_result();
        }

        // Generate a new password to make the initial one expire
        // for a non-anonymous client
        let password = if input.password.is_some() {
            debug!("Generating a new password...");
            input.new_password = Some(cotoami_db::generate_secret(None));
            input.new_password.clone()
        } else {
            None
        };

        // Log into the server to create a session
        let (client_session, http_client) = self.log_into_server(input).await?;
        let server_id = client_session.server.uuid;

        // Register the server node
        let (server, server_node, server_role, child_privileges) = spawn_blocking({
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
                let (mut server, server_role) = ds.register_server_node(
                    &server_id,
                    &url_prefix,
                    client_session.new_server_role(),
                    &operator,
                )?;

                // Save the password in the ServerNode for auto-login
                if let Some(password) = password {
                    server = ds.save_server_password(
                        &server_id,
                        &password,
                        state.read_config().try_get_owner_password()?,
                        &operator,
                    )?;
                }

                // Results
                let node = ds.node(&server_id)?.unwrap_or_else(|| unreachable!());
                Ok::<_, ServiceError>((server, node, server_role, client_session.child_privileges))
            }
        })
        .await??;
        info!("ServerNode [{}] registered.", server_node.name);

        // Create a ServerConnection
        let server_conn = ServerConnection::new(server.clone(), self.clone());
        server_conn
            .start_event_loop(http_client.clone(), child_privileges)
            .await?;
        self.server_conns().put(server_id, server_conn.clone());

        // Return a Server as a response
        let server = Server::new(
            server,
            Some(server_role),
            server_conn.not_connected(),
            server_conn.child_privileges(),
        );
        Ok(server)
    }

    pub async fn reconnect_to_server(
        &self,
        server_id: Id<Node>,
        operator: Arc<Operator>,
    ) -> Result<ServerNode, ServiceError> {
        // Disconnect before reconnect
        self.server_conns().disconnect(&server_id).await;

        // Create a new connection with the latest [ServerNode].
        let server = self.server_node(server_id, operator).await?;
        let conn = ServerConnection::new(server.clone(), self.clone());
        conn.connect().await;
        self.server_conns().put(server_id, conn);
        Ok(server)
    }

    pub async fn edit_server(
        &self,
        server_id: Id<Node>,
        values: EditServer,
        operator: Arc<Operator>,
    ) -> Result<ServerNode, ServiceError> {
        if let Err(errors) = values.validate() {
            return errors.into_result();
        }
        if !self.server_conns().contains(&server_id) {
            return Err(ServiceError::NotFound(Some(format!(
                "Server node [{server_id}] not found."
            ))));
        }

        // TODO: Set url_prefix

        // Set disabled
        if let Some(disabled) = values.disabled {
            self.set_server_disabled(server_id, disabled, operator.clone())
                .await?;
        }

        // Set password
        if let Some(password) = values.password {
            self.set_server_password(server_id, password, operator.clone())
                .await?;
        }

        // Recreate a connection with the new settings
        let server = self.reconnect_to_server(server_id, operator).await?;

        Ok(server)
    }

    async fn set_server_disabled(
        &self,
        server_id: Id<Node>,
        disabled: bool,
        operator: Arc<Operator>,
    ) -> Result<ServerNode> {
        let server = spawn_blocking({
            let db = self.db().clone();
            move || {
                let role = db
                    .new_session()?
                    .set_network_disabled(&server_id, disabled, &operator)?;
                let NetworkRole::Server(server) = role else {
                    bail!("Unexpected node role: {role:?}");
                };
                Ok(server)
            }
        })
        .await??;

        if disabled {
            // Just to publish a connection state change.
            self.server_conns().try_get(&server_id)?.disable().await;
        }
        Ok(server)
    }

    async fn set_server_password(
        &self,
        server_id: Id<Node>,
        password: String,
        operator: Arc<Operator>,
    ) -> Result<ServerNode> {
        spawn_blocking({
            let state = self.clone();
            move || {
                let ds = state.db().new_session()?;
                ds.save_server_password(
                    &server_id,
                    &password,
                    state.read_config().try_get_owner_password()?,
                    &operator,
                )
            }
        })
        .await?
    }
}
