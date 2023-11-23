use anyhow::Result;
use tokio::task::spawn_blocking;

use crate::state::{NodeState, ServerConnection};

impl NodeState {
    pub async fn restore_server_conns(&self) -> Result<()> {
        let db = self.db.clone();
        let (local_node, server_nodes) = spawn_blocking(move || {
            let mut db = db.new_session()?;
            let operator = db.local_node_as_operator()?;
            Ok::<_, anyhow::Error>((
                db.local_node_pair(&operator)?.1,
                db.all_server_nodes(&operator)?,
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
}
