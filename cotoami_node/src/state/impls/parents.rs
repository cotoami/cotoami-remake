use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::info;

use crate::state::NodeState;

impl NodeState {
    pub async fn after_first_import(&self, parent_node: Node, replicate: bool) -> Result<()> {
        let db = self.db().clone();
        let change_pubsub = self.pubsub().local_changes().clone();
        spawn_blocking(move || {
            let mut db = db.new_session()?;
            if replicate {
                if let Some(change) = db.replicate(&parent_node)? {
                    change_pubsub.publish(change, None);
                    info!("This node is now replicating [{}].", &parent_node.name);
                }
            } else {
                if let Some((link, parent_root_cotonoma, change)) =
                    db.create_link_to_parent_root(&parent_node)?
                {
                    change_pubsub.publish(change, None);
                    info!(
                        "A link to a parent root cotonoma [{}] has been created: {}",
                        parent_root_cotonoma.name, link.uuid
                    );
                }
            }
            Ok(())
        })
        .await?
    }
}
