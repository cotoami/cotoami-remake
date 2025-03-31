use anyhow::Result;
use cotoami_db::prelude::*;

use crate::{
    service::{models::NodeDetails, ServiceError},
    state::NodeState,
};

mod children;
mod clients;
mod local;
mod servers;

impl NodeState {
    pub async fn all_nodes(&self) -> Result<Vec<Node>, ServiceError> {
        self.get(move |ds| ds.all_nodes()).await
    }

    pub async fn node_details(&self, id: Id<Node>) -> Result<NodeDetails, ServiceError> {
        self.get(move |ds| {
            let node = ds.try_get_node(&id)?;
            let root = if let Some(cotonoma_id) = node.root_cotonoma_id {
                ds.cotonoma_pair(&cotonoma_id)?
            } else {
                None
            };
            Ok(NodeDetails::new(node, root))
        })
        .await
    }
}
