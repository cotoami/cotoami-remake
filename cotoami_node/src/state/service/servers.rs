use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::info;
use validator::Validate;

use crate::{
    client::HttpClient,
    service::{
        error::IntoServiceResult,
        models::{AddServerNode, CreateClientNodeSession, Server},
        RemoteNodeServiceExt, ServiceError,
    },
    state::{NodeState, ServerConnection},
};

impl NodeState {
    pub(crate) async fn add_server_node(
        &self,
        input: AddServerNode,
        operator: &Operator,
    ) -> Result<Server, ServiceError> {
        if let Err(errors) = input.validate() {
            return ("add_server_node", errors).into_result();
        }

        // Inputs
        let url_prefix = input.url_prefix.unwrap_or_else(|| unreachable!());
        let password = input.password.unwrap_or_else(|| unreachable!());
        let server_as_child = input.as_child.unwrap_or(false);

        // Get the local node
        let db = self.db().clone();
        let local_node = spawn_blocking(move || db.new_session()?.local_node()).await??;

        // Attempt to log into the server node
        let mut http_client = HttpClient::new(url_prefix)?;
        let client_session = http_client
            .create_client_node_session(CreateClientNodeSession {
                password: password.clone(),
                new_password: None, // TODO: change the password on the first login
                client: local_node,
                as_parent: Some(server_as_child),
            })
            .await?;
        info!("Successfully logged in to {}", http_client.url_prefix());
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
                let server_db_role = if server_as_child {
                    NewDatabaseRole::Child {
                        as_owner: false,
                        can_edit_links: false,
                    }
                } else {
                    NewDatabaseRole::Parent
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
        let server_conn = ServerConnection::new(&server, http_client.clone(), &self).await?;
        self.put_server_conn(&server_id, server_conn.clone());

        // Return a Server as a response
        let server = Server::new(
            server_node,
            server,
            server_conn.not_connected(),
            Some(server_db_role),
        );
        Ok(server)
    }
}
