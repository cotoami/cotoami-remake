use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{
    client::NodeSentEvent,
    state::{NodeState, ServerConnection},
};

impl NodeState {
    pub async fn restore_server_conns(&self) -> Result<()> {
        let db = self.db.clone();
        let (local_node, server_nodes) = spawn_blocking(move || {
            let mut ds = db.new_session()?;
            let operator = db.globals().local_node_as_operator()?;
            Ok::<_, anyhow::Error>((
                ds.local_node_pair(&operator)?.1,
                ds.all_server_nodes(&operator)?,
            ))
        })
        .await??;

        let mut server_conns = self.server_conns.write();
        server_conns.clear();
        for (server_node, _) in server_nodes.iter() {
            let server_conn = if server_node.disabled {
                ServerConnection::Disabled
            } else {
                ServerConnection::connect(server_node, local_node.clone(), self).await
            };
            server_conns.insert(server_node.node_id, server_conn);
        }
        Ok(())
    }

    pub async fn publish_change_to_child_servers(&self, change: &ChangelogEntry) -> Result<()> {
        for (server_id, conn) in self.read_server_conns().iter() {
            if self.is_parent(server_id) {
                continue;
            }
            match conn {
                ServerConnection::SseConnected { http_client, .. } => {
                    http_client
                        .post_event(&NodeSentEvent::Change(change.clone()))
                        .await?;
                }
                _ => (),
            }
        }
        Ok(())
    }
}
