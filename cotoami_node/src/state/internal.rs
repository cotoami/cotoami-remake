//! Methods implemented on [crate::state::NodeState]
//! that are meant to be used in this crate internally.

use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{event::local::LocalNodeEvent, service::models::NotConnected, state::NodeState};

mod changes;
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

    pub(crate) fn server_disconnected(&self, node_id: Id<Node>, not_connected: NotConnected) {
        self.pubsub().events().publish(
            LocalNodeEvent::ServerStateChanged {
                node_id,
                not_connected: Some(not_connected),
                client_as_child: None,
            },
            None,
        );
        if self.is_parent(&node_id) {
            self.pubsub().events().parent_disconnected(node_id);
        }
    }
}
