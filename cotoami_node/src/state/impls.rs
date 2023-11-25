use anyhow::Result;

use crate::state::NodeState;

mod changes;
mod events;
mod nodes;
mod parents;
mod servers;
mod session;

impl NodeState {
    pub async fn prepare(&self) -> Result<()> {
        self.init_local_node().await?;
        self.restore_server_conns().await?;
        Ok(())
    }
}
