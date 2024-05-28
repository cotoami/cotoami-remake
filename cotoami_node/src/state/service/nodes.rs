use anyhow::Result;
use cotoami_db::prelude::*;

use crate::{service::ServiceError, state::NodeState};

impl NodeState {
    pub async fn local_node(&self) -> Result<Node, ServiceError> {
        self.get(move |ds| ds.local_node()).await
    }

    pub async fn all_nodes(&self) -> Result<Vec<Node>, ServiceError> {
        self.get(move |ds| ds.all_nodes()).await
    }
}
