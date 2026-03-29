//! Legacy node-to-node wire compatibility.
//!
//! This module preserves the pre-`service::wire` MessagePack layout for the
//! WebSocket protocol so new nodes can continue talking to older deployed
//! nodes during a rolling upgrade.
//!
//! The legacy layout serialized `NodeSentEvent` and `Request` directly with
//! plain `rmp_serde::to_vec`, which uses Serde's default enum/struct
//! representation. The current protocol instead routes `Request.command`
//! through `CommandSchema` and uses named struct fields for evolution safety.
//!
//! During migration we keep sending the old WebSocket format and accept both
//! old and new formats on receive. Once all deployed nodes understand the new
//! protocol, this module can be removed.

use cotoami_db::{prelude::*, rmp_serde};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::{
    event::{local::LocalNodeEvent, remote::NodeSentEvent},
    service::{Command, Request, Response, SerializeFormat},
};

#[derive(Debug, Clone, Serialize, Deserialize)]
struct LegacyRequest {
    id: Uuid,
    accept: SerializeFormat,
    as_owner: bool,
    command: Command,
}

impl From<Request> for LegacyRequest {
    fn from(request: Request) -> Self {
        Self {
            id: request.id,
            accept: request.accept,
            as_owner: request.as_owner,
            command: request.command,
        }
    }
}

impl From<LegacyRequest> for Request {
    fn from(request: LegacyRequest) -> Self {
        Self {
            id: request.id,
            from: None,
            accept: request.accept,
            as_owner: request.as_owner,
            command: request.command,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
enum LegacyNodeSentEvent {
    Change(ChangelogEntry),
    Request(LegacyRequest),
    Response(Response),
    RemoteLocal(LocalNodeEvent),
    Error(String),
}

impl From<NodeSentEvent> for LegacyNodeSentEvent {
    fn from(event: NodeSentEvent) -> Self {
        match event {
            NodeSentEvent::Change(change) => Self::Change(change),
            NodeSentEvent::Request(request) => Self::Request(request.into()),
            NodeSentEvent::Response(response) => Self::Response(response),
            NodeSentEvent::RemoteLocal(event) => Self::RemoteLocal(event),
            NodeSentEvent::Error(message) => Self::Error(message),
        }
    }
}

impl From<LegacyNodeSentEvent> for NodeSentEvent {
    fn from(event: LegacyNodeSentEvent) -> Self {
        match event {
            LegacyNodeSentEvent::Change(change) => Self::Change(change),
            LegacyNodeSentEvent::Request(request) => Self::Request(request.into()),
            LegacyNodeSentEvent::Response(response) => Self::Response(response),
            LegacyNodeSentEvent::RemoteLocal(event) => Self::RemoteLocal(event),
            LegacyNodeSentEvent::Error(message) => Self::Error(message),
        }
    }
}

pub(crate) fn to_legacy_msgpack_vec(
    event: &NodeSentEvent,
) -> Result<Vec<u8>, rmp_serde::encode::Error> {
    rmp_serde::to_vec(&LegacyNodeSentEvent::from(event.clone()))
}

pub(crate) fn from_legacy_msgpack_slice(
    bytes: &[u8],
) -> Result<NodeSentEvent, rmp_serde::decode::Error> {
    rmp_serde::from_slice::<LegacyNodeSentEvent>(bytes).map(Into::into)
}

#[cfg(test)]
mod tests {
    use anyhow::Result;

    use super::*;
    use crate::service::{wire::to_msgpack_vec_named, Command};

    #[test]
    fn new_request_bytes_can_roundtrip_through_current_protocol() -> Result<()> {
        let request = Command::SetImageMaxSize(2048).into_request();
        let bytes = to_msgpack_vec_named(&NodeSentEvent::Request(request))?;
        let restored: NodeSentEvent = rmp_serde::from_slice(&bytes)?;

        match restored {
            NodeSentEvent::Request(request) => {
                assert!(matches!(request.command(), Command::SetImageMaxSize(2048)));
            }
            other => panic!("unexpected event after roundtrip: {other:?}"),
        }

        Ok(())
    }

    #[test]
    fn legacy_request_bytes_can_be_decoded() -> Result<()> {
        let request = Command::SetImageMaxSize(2048).into_request();
        let bytes = to_legacy_msgpack_vec(&NodeSentEvent::Request(request))?;
        let restored = from_legacy_msgpack_slice(&bytes)?;

        match restored {
            NodeSentEvent::Request(request) => {
                assert!(matches!(request.command(), Command::SetImageMaxSize(2048)));
            }
            other => panic!("unexpected event after roundtrip: {other:?}"),
        }

        Ok(())
    }
}
