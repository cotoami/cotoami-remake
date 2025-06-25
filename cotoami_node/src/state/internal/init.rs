use std::sync::Arc;

use anyhow::{Context, Result};
use cotoami_db::{ChildNodeInput, LocalNode, Principal};
use tokio::task::spawn_blocking;
use tracing::{debug, error, info};

use crate::{
    service::{error::ServiceError, models::AddClient},
    state::{error::NodeError, NodeState, ServerConnection},
};

impl NodeState {
    pub(crate) async fn init(&self) -> Result<()> {
        self.init_local_node().await?;
        self.register_owner_remote_node().await?;
        self.start_handling_local_events();
        self.restore_server_conns().await?;
        self.load_plugins()?;
        Ok(())
    }

    async fn init_local_node(&self) -> Result<LocalNode> {
        let db = self.db().clone();
        let config = self.config_arc();
        spawn_blocking(move || {
            let ds = db.new_session()?;
            let config = config.read();

            // Handle the existing node or create a new one.
            if let Some(local_node) = db.globals().local_node() {
                let owner = local_node.as_principal();

                // If there is an owner password configured,
                // [NodeConfig::owner_password] must be verified against it.
                if owner.has_password() {
                    owner
                        .authenticate(config.owner_password.as_deref())
                        .context(NodeError::OwnerAuthenticationFailed)?;

                // or configure the owner password by [NodeConfig::owner_password].
                } else if let Some(ref new_password) = config.owner_password {
                    ds.set_owner_password_if_none(new_password)?;
                    debug!("The owner password has been initialized.");
                } else {
                    debug!("Skipping authentication.");
                }
                Ok(local_node)
            } else {
                let ((local_node, node), _) = ds.init_as_node(
                    config.node_name.as_deref(),
                    config.owner_password.as_deref(),
                )?;
                info!(
                    "The local node [{}]({}) has been created",
                    node.name, node.uuid
                );
                Ok(local_node)
            }
        })
        .await?
    }

    // https://github.com/Amanieu/parking_lot/issues/197
    #[allow(clippy::await_holding_lock)]
    async fn register_owner_remote_node(&self) -> Result<()> {
        match (
            self.read_config().owner_remote_node_id,
            self.read_config().owner_remote_node_password.as_ref(),
        ) {
            (Some(node_id), Some(password)) => {
                let add_client = AddClient {
                    id: Some(node_id),
                    password: Some(password.to_owned()),
                    as_child: Some(ChildNodeInput::as_owner()),
                };
                let opr = self.local_node_as_operator()?;
                match self.add_client(add_client, Arc::new(opr)).await {
                    Ok(_) => {
                        info!("An owner remote node has been registered: {node_id}");
                    }
                    Err(service_error) => match service_error {
                        ServiceError::Request(e) if e.code == "invalid-node-role" => {
                            debug!("The owner remote node ({node_id}) has already been registered.")
                        }
                        e => error!("Error registering an owner remote node: {e:?}"),
                    },
                }
                Ok(())
            }
            _ => {
                debug!("No owner remote node settings are given.");
                Ok(())
            }
        }
    }

    /// Restores [ServerConnection] instances for the [cotoami_db::ServerNode]s
    /// of the local node.
    ///
    /// NOTE: The connections won't be made active, so you need to call
    /// [crate::state::ServerConnections::connect_all] afterward.
    async fn restore_server_conns(&self) -> Result<()> {
        let server_nodes = spawn_blocking({
            let db = self.db().clone();
            move || {
                let owner = db.globals().local_node_as_operator()?;
                db.new_session()?.all_server_nodes(&owner)
            }
        })
        .await??;

        for (server, _) in server_nodes.into_iter() {
            self.server_conns()
                .put(server.node_id, ServerConnection::new(server, self.clone()));
        }
        Ok(())
    }

    fn load_plugins(&self) -> Result<()> {
        let plugins_dir = self.config_arc().read().plugins_dir();
        crate::create_dir_if_not_exist(&plugins_dir)?;
        self.load_plugins_from_dir(plugins_dir)?;
        Ok(())
    }
}
