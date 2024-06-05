use cotoami_db::prelude::*;

use crate::service::models::NotConnected;

#[derive(Debug, Clone, serde::Serialize)]
pub enum LocalNodeEvent {
    ServerDisconnected {
        server_node_id: Id<Node>,
        reason: NotConnected,
    },
    ParentDisconnected(Id<Node>),
}
