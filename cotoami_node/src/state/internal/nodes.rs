use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::state::NodeState;

impl NodeState {
    pub(crate) async fn node(&self, id: Id<Node>) -> Result<Option<Node>> {
        let db = self.db().clone();
        spawn_blocking(move || db.new_session()?.node(&id)).await?
    }

    pub(crate) async fn create_agent_node(self, name: String, icon: Vec<u8>) -> Result<Node> {
        spawn_blocking({
            move || {
                let ds = self.db().new_session()?;
                let (node, log) = ds.create_agent_node(&name, &icon)?;
                self.pubsub().publish_change(log);
                Ok(node)
            }
        })
        .await?
    }
}
