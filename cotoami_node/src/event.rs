use cotoami_db::ChangelogEntry;

use crate::service::{Request, Response};

#[derive(Clone, serde::Serialize, serde::Deserialize)]
pub(crate) enum NodeSentEvent {
    Connected,
    Change(ChangelogEntry),
    Request(Request),
    Response(Response),
    Error(String),
}
