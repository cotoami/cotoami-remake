//! Methods implemented on [crate::state::NodeState]
//! that are meant to be used in this crate internally.

use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::state::NodeState;

mod changes;
mod events;
mod init;
mod parents;

impl NodeState {
    pub(crate) async fn local_node_pair(&self) -> Result<(LocalNode, Node)> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let operator = db.globals().local_node_as_operator()?;
            db.new_session()?.local_node_pair(&operator)
        })
        .await?
    }

    pub(crate) async fn as_operator(&self, node_id: Id<Node>) -> Result<Option<Operator>> {
        let db = self.db().clone();
        spawn_blocking(move || db.new_session()?.as_operator(node_id)).await?
    }
}
