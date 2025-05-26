use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;

use crate::{service::ServiceError, state::NodeState};

impl NodeState {
    pub async fn parent_nodes(
        &self,
        operator: Arc<Operator>,
    ) -> Result<Vec<ParentNode>, ServiceError> {
        self.get(move |ds| ds.parent_nodes(&operator)).await
    }
}
