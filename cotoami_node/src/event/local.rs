use cotoami_db::prelude::*;

use crate::service::models::NotConnected;

#[derive(Debug, Clone, serde::Serialize)]
pub enum LocalNodeEvent {
    ServerDisconnected {
        node_id: Id<Node>,
        reason: NotConnected,
    },
    ParentSyncStart {
        node_id: Id<Node>,
        parent_description: String,
    },
    ParentSyncProgress {
        node_id: Id<Node>,
        progress: i64,
        max: i64,
    },
    ParentSyncEnd {
        node_id: Id<Node>,
        range: Option<(i64, i64)>,
        error: Option<String>,
    },
    ParentDisconnected(Id<Node>),
}
