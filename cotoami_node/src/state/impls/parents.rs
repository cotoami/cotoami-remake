use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::info;

use crate::state::NodeState;

impl NodeState {
    pub async fn create_link_to_parent_root(&self, parent_id: Id<Node>) -> Result<()> {
        let db = self.db().clone();
        let change_pubsub = self.pubsub().local_changes().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;
            if let Some((link, parent_cotonoma, change)) =
                ds.create_link_to_parent_root(&parent_id)?
            {
                change_pubsub.publish(change, None);
                info!(
                    "A link to the parent cotonoma [{}] has been created: {}",
                    parent_cotonoma.name, link.uuid
                );
            }
            Ok(())
        })
        .await?
    }
}
