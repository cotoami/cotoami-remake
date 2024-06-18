use anyhow::{Context, Result};
use cotoami_db::Principal;
use futures::StreamExt;
use tokio::task::spawn_blocking;
use tracing::{debug, info};

use crate::{
    event::local::LocalNodeEvent,
    state::{NodeState, ServerConnection},
};

impl NodeState {
    pub(crate) async fn init(&self) -> Result<()> {
        self.init_local_node().await?;
        self.start_handling_local_events();
        self.restore_server_conns().await?;
        Ok(())
    }

    async fn init_local_node(&self) -> Result<()> {
        let db = self.db().clone();
        let config = self.config().clone();
        spawn_blocking(move || {
            if let Some(local_node) = db.globals().local_node() {
                if local_node.has_password() {
                    local_node
                        .authenticate(config.owner_password.as_deref())
                        .context("Owner authentication has been failed.")?;
                } else {
                    if let Some(ref new_password) = config.owner_password {
                        db.new_session()?.set_owner_password_if_none(new_password)?;
                        debug!("The owner password has been initialized.");
                    } else {
                        debug!("Skipping authentication.");
                    }
                }
            } else {
                let ((_, node), _) = db.new_session()?.init_as_node(
                    config.node_name.as_deref(),
                    config.owner_password.as_deref(),
                )?;
                info!(
                    "The local node [{}]({}) has been created",
                    node.name, node.uuid
                );
            }
            Ok(())
        })
        .await?
    }

    fn start_handling_local_events(&self) {
        let this = self.clone();
        self.spawn_task(async move {
            let mut events = this.pubsub().events().subscribe(None::<()>);
            while let Some(event) = events.next().await {
                debug!("Internal event: {event:?}");
                match event {
                    LocalNodeEvent::ParentDisconnected(parent_id) => {
                        this.parent_services().remove(&parent_id);
                    }
                    _ => (),
                }
            }
        });
    }

    async fn restore_server_conns(&self) -> Result<()> {
        let server_nodes = spawn_blocking({
            let db = self.db().clone();
            move || {
                let mut ds = db.new_session()?;
                let operator = db.globals().local_node_as_operator()?;
                ds.all_server_nodes(&operator)
            }
        })
        .await??;

        for (server, _) in server_nodes.into_iter() {
            self.server_conns()
                .put(server.node_id, ServerConnection::new(server, self.clone()));
        }
        Ok(())
    }
}
