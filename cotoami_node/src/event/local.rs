use cotoami_db::prelude::*;

use crate::service::models::NotConnected;

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub enum LocalNodeEvent {
    ServerStateChanged {
        node_id: Id<Node>,
        not_connected: Option<NotConnected>,
        client_as_child: Option<ChildNode>,
    },
    ParentSyncStart {
        node_id: Id<Node>,
        parent_description: String,
    },
    ParentSyncProgress {
        node_id: Id<Node>,
        progress: i64,
        total: i64,
    },
    ParentSyncEnd {
        node_id: Id<Node>,
        range: Option<(i64, i64)>,
        error: Option<String>,
    },
    ParentDisconnected {
        node_id: Id<Node>,
    },
    ClientConnected {
        node_id: Id<Node>,
    },
    ClientDisconnected {
        node_id: Id<Node>,
        error: Option<String>,
    },
}
