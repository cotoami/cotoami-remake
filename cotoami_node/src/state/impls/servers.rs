use anyhow::Result;
use tokio::task::spawn_blocking;

use crate::state::{NodeState, ServerConnection};

impl NodeState {
    pub(crate) async fn restore_server_conns(&self) -> Result<()> {
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

        let mut server_conns = self.write_server_conns();
        server_conns.clear();
        for (server, _) in server_nodes.iter() {
            let server_conn = ServerConnection::connect(server, local_node.clone(), self).await;
            server_conns.insert(server.node_id, server_conn);
        }
        Ok(())
    }
}
