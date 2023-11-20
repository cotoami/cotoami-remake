use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::info;

use crate::ChangePubsub;

pub(crate) async fn after_first_import(
    parent_node: Node,
    replicate: bool,
    db: Arc<Database>,
    change_pubsub: Arc<ChangePubsub>,
) -> Result<()> {
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
