use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{service::ServiceError, state::NodeState};

impl NodeState {
    pub async fn child_node(
        &self,
        id: Id<Node>,
        operator: Arc<Operator>,
    ) -> Result<ChildNode, ServiceError> {
        self.get(move |ds| ds.try_get_child_node(&id, &operator))
            .await
    }

    pub async fn edit_child(
        &self,
        node_id: Id<Node>,
        values: ChildNodeInput,
        operator: Arc<Operator>,
    ) -> Result<ChildNode, ServiceError> {
        let child_node = spawn_blocking({
            let db = self.db().clone();
            move || {
                db.new_session()?
                    .edit_child_node(&node_id, &values, &operator)
            }
        })
        .await??;

        // Disconnect from the child node to allow it to reload the settings.
        self.disconnect_from(&node_id).await;

        Ok(child_node)
    }
}
