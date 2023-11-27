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
                ServerConnection::connect_sse(server_node, local_node.clone(), self).await
            };
            server_conns.insert(server_node.node_id, server_conn);
        }
        Ok(())
    }

    pub async fn publish_change_to_child_servers(&self, change: &ChangelogEntry) -> Result<()> {
        let child_servers: Vec<_> = self
            .read_server_conns()
            .iter()
            .filter_map(|(server_id, conn)| {
                if self.is_parent(server_id) {
                    return None;
                }
                match conn {
                    ServerConnection::SseConnected { http_client, .. } => Some(http_client.clone()),
                    _ => None,
                }
            })
            .collect();

        for child_server in child_servers {
            child_server
                .post_event(&NodeSentEvent::Change(change.clone()))
                .await?;
        }

        Ok(())
    }
}
