use anyhow::{Context, Result};
use cotoami_db::Principal;
use futures::StreamExt;
use tokio::task::spawn_blocking;
use tracing::{debug, info};

use crate::{
    event::local::LocalNodeEvent,
    state::{NodeState, ServerConnection, ServerConnections},
};

impl NodeState {
    pub(crate) async fn init(&self) -> Result<()> {
        self.init_local_node().await?;
        self.restore_server_conns().await?;
        self.handle_local_events();
        Ok(())
    }

    async fn init_local_node(&self) -> Result<()> {
        let db = self.db().clone();
        let config = self.config().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;

            // If the local node already exists,
            // its name and password can be changed via config
            if db.globals().has_local_node() {
                let opr = db.globals().local_node_as_operator()?;
                let (local, node) = ds.local_node_pair(&opr)?;

                // Change the node name
                if let Some(name) = config.node_name.as_deref() {
                    if name != node.name {
                        // Ignoring the changelog since this function is called during
                        // the node initialization (there should be no child nodes connected).
                        let (node, _) = ds.rename_local_node(name, &opr)?;
                        info!("The node name has been changed to [{}].", node.name);
                    }
                }

                // Owner authentication
                local
                    .authenticate(config.owner_password.as_deref())
                    .context("Owner authentication has been failed.")?;

                return Ok(());
            }

            // Initialize the local node
            let ((_, node), _) = ds.init_as_node(
                config.node_name.as_deref(),
                config.owner_password.as_deref(),
            )?;
            info!(
                "The local node [{}]({}) has been created",
                node.name, node.uuid
            );
            Ok(())
        })
        .await?
    }

    async fn restore_server_conns(&self) -> Result<()> {
        let (local_node, server_nodes) = spawn_blocking({
            let db = self.db().clone();
            move || {
                let mut ds = db.new_session()?;
                let operator = db.globals().local_node_as_operator()?;
                Ok::<_, anyhow::Error>((
                    ds.local_node_pair(&operator)?.1,
                    ds.all_server_nodes(&operator)?,
                ))
            }
        })
        .await??;

        let mut server_conns = ServerConnections::new();
        for (server, _) in server_nodes.iter() {
            server_conns.insert(
                server.node_id,
                ServerConnection::connect(server, local_node.clone(), self).await,
            );
        }
        *self.write_server_conns() = server_conns;
        Ok(())
    }

    fn handle_local_events(&self) {
        let this = self.clone();
        tokio::spawn(async move {
            let mut events = this.pubsub().events().subscribe(None::<()>);
            while let Some(event) = events.next().await {
                debug!("Internal event: {event:?}");
                match event {
                    LocalNodeEvent::ParentDisconnected(parent_id) => {
                        this.remove_parent_service(&parent_id);
                    }
                    _ => (),
                }
            }
        });
    }
}
