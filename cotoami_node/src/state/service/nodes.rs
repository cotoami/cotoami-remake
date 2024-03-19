use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{service::ServiceError, state::NodeState};

impl NodeState {
    pub async fn local_node(&self) -> Result<Node, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || Ok(db.new_session()?.local_node()?)).await?
    }

    pub async fn all_nodes(&self) -> Result<Vec<Node>, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || Ok(db.new_session()?.all_nodes()?)).await?
    }
}
