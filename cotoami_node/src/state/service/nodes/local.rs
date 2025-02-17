use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;

use crate::{service::ServiceError, state::NodeState};

impl NodeState {
    pub async fn local_node(&self) -> Result<Node, ServiceError> {
        self.get(move |ds| ds.local_node()).await
    }

    pub async fn local_node_root(&self) -> Result<Option<(Cotonoma, Coto)>, ServiceError> {
        self.get(move |ds| ds.local_node_root()).await
    }

    pub async fn set_local_node_icon(
        self,
        icon: bytes::Bytes,
        operator: Arc<Operator>,
    ) -> Result<Node, ServiceError> {
        self.change_local(move |ds| ds.set_local_node_icon(icon.as_ref(), operator.as_ref()))
            .await
    }
}
