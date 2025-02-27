use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::{debug, error, info};

use crate::{event::local::LocalNodeEvent, service::NodeService, state::NodeState};

impl NodeState {
    pub(crate) fn register_parent_service(
        &self,
        parent_id: Id<Node>,
        service: Box<dyn NodeService>,
    ) {
        debug!("Parent service being registered: {parent_id}");
        self.parent_services()
            .put(parent_id, dyn_clone::clone_box(&*service));
        self.pubsub()
            .publish_event(LocalNodeEvent::ParentRegistered { node_id: parent_id });

        // A task syncing with the parent
        self.spawn_task({
            let this = self.clone();
            async move {
                let description = service.description().to_string();
                match this.sync_with_parent(parent_id, service).await {
                    Ok(Some((import_from, _))) => {
                        // Create an ito to the parent cotonoma after the first import.
                        if import_from == 1 {
                            debug!("The first import has been completed.");
                            if let Err(e) = this.pin_parent_root(parent_id).await {
                                error!("Error creating an ito: {e:?}");
                            }
                        }
                    }
                    Ok(None) => (),
                    Err(e) => {
                        if let Ok(conn) = this.server_conns().try_get(&parent_id) {
                            error!("Error syncing with ({description}): {e:?}");
                            conn.disconnect(Some("sync-error"));
                        }
                    }
                }
            }
        });
    }

    async fn pin_parent_root(&self, parent_id: Id<Node>) -> Result<()> {
        let db = self.db().clone();
        let change_pubsub = self.pubsub().changes().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;
            if let Some((ito, parent_cotonoma, change)) = ds.pin_parent_root(&parent_id)? {
                change_pubsub.publish(change, None);
                info!(
                    "An ito to the parent cotonoma [{}] has been created: {}",
                    parent_cotonoma.name, ito.uuid
                );
            }
            Ok(())
        })
        .await?
    }
}
