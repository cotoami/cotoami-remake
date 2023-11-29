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
        // Collect the connections to the child servers
        let child_conns: Vec<_> = self
            .read_server_conns()
            .iter()
            .filter_map(|(server_id, conn)| {
                if self.is_parent(server_id) {
                    None
                } else {
                    Some(conn.clone())
                }
            })
            .collect();

        // Sent the change to the child servers
        for child_conn in child_conns {
            match child_conn {
                ServerConnection::SseConnected(sse_conn) => {
                    sse_conn
                        .http_client()
                        .post_event(&NodeSentEvent::Change(change.clone()))
                        .await?;
                }
                _ => (),
            }
        }

        Ok(())
    }
}
