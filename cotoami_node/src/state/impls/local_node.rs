use anyhow::{Context, Result};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::info;

use crate::state::NodeState;

impl NodeState {
    pub(crate) async fn local_node(&self) -> Result<Node> {
        let db = self.db().clone();
        spawn_blocking(move || Ok(db.new_session()?.local_node()?)).await?
    }

    pub(crate) async fn init_local_node(&self) -> Result<()> {
        let db = self.db().clone();
        let config = self.config().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;
            let owner_password = config.owner_password();

            // If the local node already exists,
            // its name and password can be changed via config
            if db.globals().has_local_node_initialized() {
                let opr = db.globals().local_node_as_operator()?;
                let (ext, node) = ds.local_node_pair(&opr)?;
                if let Some(name) = config.node_name.as_deref() {
                    if name != node.name {
                        // Ignoring the changelog since this function is called during
                        // the server startup (there should be no child nodes connected).
                        let (node, _) = ds.rename_local_node(name, &opr)?;
                        info!("The node name has been changed to [{}].", node.name);
                    }
                }

                if config.change_owner_password {
                    ds.change_owner_password(owner_password)?;
                    info!("The owner password has been changed.");
                } else {
                    ext.verify_password(owner_password)
                        .context("Config::owner_password couldn't be verified.")?;
                }
                return Ok(());
            }

            // Initialize the local node
            let name = config.node_name.as_deref();
            let ((_, node), _) = ds.init_as_node(name, Some(owner_password))?;
            info!(
                "The local node [{}]({}) has been created",
                node.name, node.uuid
            );
            Ok(())
        })
        .await?
    }
}
