//! Transport-level schemas for the node service protocol.
//!
//! This module defines the stable wire contract used when service requests are
//! serialized and deserialized for communication between cotoami nodes. These
//! schema types are intentionally separate from the internal domain types such
//! as [`super::Command`] and [`super::Request`].
//!
//! The separation is important because internal service types are free to
//! evolve for implementation reasons, while the wire protocol needs a more
//! deliberate compatibility policy. If node-to-node communication serialized
//! the internal enums and structs directly, ordinary refactors such as changing
//! variant layouts, reordering fields, or replacing borrowed data with owned
//! data could accidentally break interoperability between versions.
//!
//! The role of this module is therefore:
//!
//! - to define explicit transport-facing schema types with stable names
//! - to decouple the external protocol from in-process service/domain design
//! - to centralize compatibility decisions in one place
//! - to let multiple encodings such as JSON and MessagePack share the same
//!   logical request/command schema
//!
//! The typical flow is:
//!
//! - outgoing: internal service type -> schema type -> serialized bytes
//! - incoming: serialized bytes -> schema type -> internal service type
//!
//! `CommandSchema` is the dedicated transport schema at this layer. `Request`
//! itself acts as the wire envelope, while its command payload is translated
//! through `CommandSchema` so the protocol stays decoupled from the internal
//! service enum layout.
//!
//! For MessagePack specifically, these schemas are intended to be serialized
//! with named struct fields via [`to_msgpack_vec_named`] so that field-order
//! changes and additive fields can evolve more safely than with the default
//! compact positional encoding.

use cotoami_db::{prelude::*, rmp_serde};
use serde::{ser::SerializeStruct, Deserialize, Deserializer, Serialize, Serializer};
use uuid::Uuid;

use crate::service::{models::*, Command, Request, SerializeFormat};

impl Serialize for Request {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        // `Request` remains the internal/public envelope type, but its
        // `command` field is serialized through `CommandSchema` so the wire
        // protocol is insulated from internal `Command` enum refactors.
        let mut state = serializer.serialize_struct("Request", 4)?;
        state.serialize_field("id", &self.id)?;
        state.serialize_field("accept", &self.accept)?;
        state.serialize_field("as_owner", &self.as_owner)?;
        state.serialize_field("command", &CommandSchema::from(self.command.clone()))?;
        state.end()
    }
}

impl<'de> Deserialize<'de> for Request {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        // Mirror the serialize path: decode the request envelope directly, but
        // parse the `command` payload via `CommandSchema` before rebuilding the
        // in-process `Request`.
        #[derive(Deserialize)]
        struct RequestEnvelope {
            id: Uuid,
            accept: SerializeFormat,
            #[serde(default)]
            as_owner: bool,
            command: CommandSchema,
        }

        let envelope = RequestEnvelope::deserialize(deserializer)?;
        Ok(Self {
            id: envelope.id,
            from: None,
            accept: envelope.accept,
            as_owner: envelope.as_owner,
            command: envelope.command.into(),
        })
    }
}

/// Dedicated schema for `Command`.
///
/// `Command` is the core service-domain enum and is likely to evolve for
/// implementation reasons. This schema freezes the transport contract in terms
/// of explicit variant names. Nested payload structs currently reuse the
/// internal service types directly, and can be split into dedicated schemas
/// later if their wire representation ever needs to diverge.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub(crate) enum CommandSchema {
    LocalNode,
    LocalServer,
    SetLocalNodeIcon {
        icon: Bytes,
    },
    SetImageMaxSize {
        size: i32,
    },
    EnableAnonymousRead {
        enable: bool,
    },
    InitialDataset,
    ChunkOfChanges {
        from: i64,
    },
    NodeDetails {
        id: Id<Node>,
    },
    CreateClientNodeSession {
        session: CreateClientNodeSession,
    },
    TryLogIntoServer {
        login: LogIntoServer,
    },
    AddServer {
        server: LogIntoServer,
    },
    EditServer {
        id: Id<Node>,
        values: EditServer,
    },
    RecentClients {
        pagination: Pagination,
    },
    ClientNode {
        id: Id<Node>,
    },
    AddClient {
        client: AddClient,
    },
    ResetClientPassword {
        id: Id<Node>,
    },
    EditClient {
        id: Id<Node>,
        values: EditClient,
    },
    ChildNode {
        id: Id<Node>,
    },
    EditChild {
        id: Id<Node>,
        values: ChildNodeInput,
    },
    RecentCotonomas {
        #[serde(default)]
        node: Option<Id<Node>>,
        pagination: Pagination,
    },
    CotonomasByPrefix {
        prefix: String,
        #[serde(default)]
        nodes: Option<Vec<Id<Node>>>,
    },
    CotonomasByPartial {
        partial: String,
        #[serde(default)]
        nodes: Option<Vec<Id<Node>>>,
    },
    Cotonoma {
        id: Id<Cotonoma>,
    },
    CotonomaDetails {
        id: Id<Cotonoma>,
    },
    CotonomaByCotoId {
        id: Id<Coto>,
    },
    CotonomaByName {
        name: String,
        node: Id<Node>,
    },
    SubCotonomas {
        id: Id<Cotonoma>,
        pagination: Pagination,
    },
    RecentCotos {
        scope: Scope,
        only_cotonomas: bool,
        pagination: Pagination,
    },
    GeolocatedCotos {
        scope: Scope,
    },
    CotosInGeoBounds {
        southwest: Geolocation,
        northeast: Geolocation,
    },
    SearchCotos {
        query: String,
        scope: Scope,
        only_cotonomas: bool,
        pagination: Pagination,
    },
    CotoDetails {
        id: Id<Coto>,
    },
    GraphFromCoto {
        coto: Id<Coto>,
    },
    GraphFromCotonoma {
        cotonoma: Id<Cotonoma>,
    },
    PostCoto {
        input: CotoInput<'static>,
        post_to: Id<Cotonoma>,
    },
    PostCotonoma {
        input: CotonomaInput<'static>,
        post_to: Id<Cotonoma>,
    },
    EditCoto {
        id: Id<Coto>,
        diff: CotoContentDiff<'static>,
    },
    Promote {
        id: Id<Coto>,
    },
    DeleteCoto {
        id: Id<Coto>,
    },
    Repost {
        id: Id<Coto>,
        dest: Id<Cotonoma>,
    },
    RenameCotonoma {
        id: Id<Cotonoma>,
        name: String,
    },
    Ito {
        id: Id<Ito>,
    },
    SiblingItos {
        coto: Id<Coto>,
        #[serde(default)]
        node: Option<Id<Node>>,
    },
    CreateIto {
        input: ItoInput<'static>,
    },
    EditIto {
        id: Id<Ito>,
        diff: ItoContentDiff<'static>,
    },
    DeleteIto {
        id: Id<Ito>,
    },
    ChangeItoOrder {
        id: Id<Ito>,
        new_order: i32,
    },
    OthersLastPostedAt,
    MarkAsRead {
        #[serde(default)]
        node: Option<Id<Node>>,
    },
    PostSubcoto {
        source_coto: Id<Coto>,
        input: CotoInput<'static>,
        #[serde(default)]
        post_to: Option<Id<Cotonoma>>,
        #[serde(default)]
        order: Option<i32>,
    },
}

impl From<Command> for CommandSchema {
    fn from(command: Command) -> Self {
        match command {
            Command::LocalNode => Self::LocalNode,
            Command::LocalServer => Self::LocalServer,
            Command::SetLocalNodeIcon { icon } => Self::SetLocalNodeIcon { icon },
            Command::SetImageMaxSize(size) => Self::SetImageMaxSize { size },
            Command::EnableAnonymousRead { enable } => Self::EnableAnonymousRead { enable },
            Command::InitialDataset => Self::InitialDataset,
            Command::ChunkOfChanges { from } => Self::ChunkOfChanges { from },
            Command::NodeDetails { id } => Self::NodeDetails { id },
            Command::CreateClientNodeSession(session) => Self::CreateClientNodeSession { session },
            Command::TryLogIntoServer(login) => Self::TryLogIntoServer { login },
            Command::AddServer(server) => Self::AddServer { server },
            Command::EditServer { id, values } => Self::EditServer { id, values },
            Command::RecentClients { pagination } => Self::RecentClients { pagination },
            Command::ClientNode { id } => Self::ClientNode { id },
            Command::AddClient(client) => Self::AddClient { client },
            Command::ResetClientPassword { id } => Self::ResetClientPassword { id },
            Command::EditClient { id, values } => Self::EditClient { id, values },
            Command::ChildNode { id } => Self::ChildNode { id },
            Command::EditChild { id, values } => Self::EditChild { id, values },
            Command::RecentCotonomas { node, pagination } => {
                Self::RecentCotonomas { node, pagination }
            }
            Command::CotonomasByPrefix { prefix, nodes } => {
                Self::CotonomasByPrefix { prefix, nodes }
            }
            Command::CotonomasByPartial { partial, nodes } => {
                Self::CotonomasByPartial { partial, nodes }
            }
            Command::Cotonoma { id } => Self::Cotonoma { id },
            Command::CotonomaDetails { id } => Self::CotonomaDetails { id },
            Command::CotonomaByCotoId { id } => Self::CotonomaByCotoId { id },
            Command::CotonomaByName { name, node } => Self::CotonomaByName { name, node },
            Command::SubCotonomas { id, pagination } => Self::SubCotonomas { id, pagination },
            Command::RecentCotos {
                scope,
                only_cotonomas,
                pagination,
            } => Self::RecentCotos {
                scope,
                only_cotonomas,
                pagination,
            },
            Command::GeolocatedCotos { scope } => Self::GeolocatedCotos { scope },
            Command::CotosInGeoBounds {
                southwest,
                northeast,
            } => Self::CotosInGeoBounds {
                southwest,
                northeast,
            },
            Command::SearchCotos {
                query,
                scope,
                only_cotonomas,
                pagination,
            } => Self::SearchCotos {
                query,
                scope,
                only_cotonomas,
                pagination,
            },
            Command::CotoDetails { id } => Self::CotoDetails { id },
            Command::GraphFromCoto { coto } => Self::GraphFromCoto { coto },
            Command::GraphFromCotonoma { cotonoma } => Self::GraphFromCotonoma { cotonoma },
            Command::PostCoto { input, post_to } => Self::PostCoto { input, post_to },
            Command::PostCotonoma { input, post_to } => Self::PostCotonoma { input, post_to },
            Command::EditCoto { id, diff } => Self::EditCoto { id, diff },
            Command::Promote { id } => Self::Promote { id },
            Command::DeleteCoto { id } => Self::DeleteCoto { id },
            Command::Repost { id, dest } => Self::Repost { id, dest },
            Command::RenameCotonoma { id, name } => Self::RenameCotonoma { id, name },
            Command::Ito { id } => Self::Ito { id },
            Command::SiblingItos { coto, node } => Self::SiblingItos { coto, node },
            Command::CreateIto(input) => Self::CreateIto { input },
            Command::EditIto { id, diff } => Self::EditIto { id, diff },
            Command::DeleteIto { id } => Self::DeleteIto { id },
            Command::ChangeItoOrder { id, new_order } => Self::ChangeItoOrder { id, new_order },
            Command::OthersLastPostedAt => Self::OthersLastPostedAt,
            Command::MarkAsRead { node } => Self::MarkAsRead { node },
            Command::PostSubcoto {
                source_coto,
                input,
                post_to,
                order,
            } => Self::PostSubcoto {
                source_coto,
                input,
                post_to,
                order,
            },
        }
    }
}

impl From<CommandSchema> for Command {
    fn from(command: CommandSchema) -> Self {
        match command {
            CommandSchema::LocalNode => Self::LocalNode,
            CommandSchema::LocalServer => Self::LocalServer,
            CommandSchema::SetLocalNodeIcon { icon } => Self::SetLocalNodeIcon { icon },
            CommandSchema::SetImageMaxSize { size } => Self::SetImageMaxSize(size),
            CommandSchema::EnableAnonymousRead { enable } => Self::EnableAnonymousRead { enable },
            CommandSchema::InitialDataset => Self::InitialDataset,
            CommandSchema::ChunkOfChanges { from } => Self::ChunkOfChanges { from },
            CommandSchema::NodeDetails { id } => Self::NodeDetails { id },
            CommandSchema::CreateClientNodeSession { session } => {
                Self::CreateClientNodeSession(session)
            }
            CommandSchema::TryLogIntoServer { login } => Self::TryLogIntoServer(login),
            CommandSchema::AddServer { server } => Self::AddServer(server),
            CommandSchema::EditServer { id, values } => Self::EditServer { id, values },
            CommandSchema::RecentClients { pagination } => Self::RecentClients { pagination },
            CommandSchema::ClientNode { id } => Self::ClientNode { id },
            CommandSchema::AddClient { client } => Self::AddClient(client),
            CommandSchema::ResetClientPassword { id } => Self::ResetClientPassword { id },
            CommandSchema::EditClient { id, values } => Self::EditClient { id, values },
            CommandSchema::ChildNode { id } => Self::ChildNode { id },
            CommandSchema::EditChild { id, values } => Self::EditChild { id, values },
            CommandSchema::RecentCotonomas { node, pagination } => {
                Self::RecentCotonomas { node, pagination }
            }
            CommandSchema::CotonomasByPrefix { prefix, nodes } => {
                Self::CotonomasByPrefix { prefix, nodes }
            }
            CommandSchema::CotonomasByPartial { partial, nodes } => {
                Self::CotonomasByPartial { partial, nodes }
            }
            CommandSchema::Cotonoma { id } => Self::Cotonoma { id },
            CommandSchema::CotonomaDetails { id } => Self::CotonomaDetails { id },
            CommandSchema::CotonomaByCotoId { id } => Self::CotonomaByCotoId { id },
            CommandSchema::CotonomaByName { name, node } => Self::CotonomaByName { name, node },
            CommandSchema::SubCotonomas { id, pagination } => Self::SubCotonomas { id, pagination },
            CommandSchema::RecentCotos {
                scope,
                only_cotonomas,
                pagination,
            } => Self::RecentCotos {
                scope,
                only_cotonomas,
                pagination,
            },
            CommandSchema::GeolocatedCotos { scope } => Self::GeolocatedCotos { scope },
            CommandSchema::CotosInGeoBounds {
                southwest,
                northeast,
            } => Self::CotosInGeoBounds {
                southwest,
                northeast,
            },
            CommandSchema::SearchCotos {
                query,
                scope,
                only_cotonomas,
                pagination,
            } => Self::SearchCotos {
                query,
                scope,
                only_cotonomas,
                pagination,
            },
            CommandSchema::CotoDetails { id } => Self::CotoDetails { id },
            CommandSchema::GraphFromCoto { coto } => Self::GraphFromCoto { coto },
            CommandSchema::GraphFromCotonoma { cotonoma } => Self::GraphFromCotonoma { cotonoma },
            CommandSchema::PostCoto { input, post_to } => Self::PostCoto { input, post_to },
            CommandSchema::PostCotonoma { input, post_to } => Self::PostCotonoma { input, post_to },
            CommandSchema::EditCoto { id, diff } => Self::EditCoto { id, diff },
            CommandSchema::Promote { id } => Self::Promote { id },
            CommandSchema::DeleteCoto { id } => Self::DeleteCoto { id },
            CommandSchema::Repost { id, dest } => Self::Repost { id, dest },
            CommandSchema::RenameCotonoma { id, name } => Self::RenameCotonoma { id, name },
            CommandSchema::Ito { id } => Self::Ito { id },
            CommandSchema::SiblingItos { coto, node } => Self::SiblingItos { coto, node },
            CommandSchema::CreateIto { input } => Self::CreateIto(input),
            CommandSchema::EditIto { id, diff } => Self::EditIto { id, diff },
            CommandSchema::DeleteIto { id } => Self::DeleteIto { id },
            CommandSchema::ChangeItoOrder { id, new_order } => {
                Self::ChangeItoOrder { id, new_order }
            }
            CommandSchema::OthersLastPostedAt => Self::OthersLastPostedAt,
            CommandSchema::MarkAsRead { node } => Self::MarkAsRead { node },
            CommandSchema::PostSubcoto {
                source_coto,
                input,
                post_to,
                order,
            } => Self::PostSubcoto {
                source_coto,
                input,
                post_to,
                order,
            },
        }
    }
}

/// Serialize a value into MessagePack while preserving field names for structs.
///
/// This helper exists for transport-level payloads that need schema evolution.
/// `rmp_serde::to_vec` prefers a compact positional representation for structs,
/// which means a struct may be encoded like an array of fields in declaration
/// order instead of a map keyed by field name. That compact form is efficient,
/// but it is fragile for long-lived protocols:
///
/// - reordering fields can break deserialization
/// - inserting a new field before existing ones can break deserialization
/// - `#[serde(default)]` and optional fields cannot reliably help if the data is
///   matched by position rather than by field name
///
/// By forcing struct-map encoding, this function writes MessagePack objects with
/// explicit field names. That makes the wire format much more tolerant to normal
/// schema evolution such as adding defaulted fields or reordering fields without
/// changing their names.
///
/// This should be preferred over plain `rmp_serde::to_vec` for node-to-node wire
/// contracts and any other serialized data that must remain compatible across
/// versions. For short-lived internal payloads where compactness matters more
/// than evolution safety, plain `rmp_serde::to_vec` may still be appropriate.
pub fn to_msgpack_vec_named<T: Serialize>(value: &T) -> Result<Vec<u8>, rmp_serde::encode::Error> {
    let mut buf = Vec::new();
    let mut serializer = rmp_serde::Serializer::new(&mut buf).with_struct_map();
    value.serialize(&mut serializer)?;
    Ok(buf)
}

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use serde::{Deserialize, Serialize};
    use serde_json::json;

    use super::*;
    use crate::service::{models::Pagination, Command};

    #[test]
    fn request_json_uses_explicit_wire_schema() -> Result<()> {
        let cotonoma_id = Id::generate();
        let request = Request {
            id: Uuid::nil(),
            from: None,
            accept: SerializeFormat::Json,
            as_owner: false,
            command: Command::PostCoto {
                input: CotoInput::new("hello").summary("summary"),
                post_to: cotonoma_id,
            },
        };

        let value = serde_json::to_value(&request)?;

        assert_eq!(
            value,
            json!({
                "id": Uuid::nil(),
                "accept": "Json",
                "as_owner": false,
                "command": {
                    "type": "post_coto",
                    "input": {
                        "content": "hello",
                        "summary": "summary",
                        "media_content": null,
                        "geolocation": null,
                        "datetime_range": null
                    },
                    "post_to": cotonoma_id
                }
            })
        );

        Ok(())
    }

    #[test]
    fn request_json_roundtrip_restores_internal_command() -> Result<()> {
        let coto_id = Id::generate();
        let request = Request {
            id: Uuid::nil(),
            from: None,
            accept: SerializeFormat::MessagePack,
            as_owner: true,
            command: Command::SearchCotos {
                query: "query".into(),
                scope: Scope::Cotonoma((coto_id, CotonomaScope::Depth(3))),
                only_cotonomas: true,
                pagination: Pagination {
                    page: 2,
                    page_size: Some(20),
                },
            },
        };

        let json = serde_json::to_vec(&request)?;
        let restored: Request = serde_json::from_slice(&json)?;

        assert_eq!(restored.id(), request.id());
        assert!(matches!(restored.accept(), SerializeFormat::MessagePack));
        assert!(restored.as_owner());
        match restored.command() {
            Command::SearchCotos {
                query,
                scope,
                only_cotonomas,
                pagination,
            } => {
                assert_eq!(query, "query");
                assert_eq!(scope, Scope::Cotonoma((coto_id, CotonomaScope::Depth(3))));
                assert!(only_cotonomas);
                assert_eq!(pagination.page, 2);
                assert_eq!(pagination.page_size, Some(20));
            }
            other => panic!("unexpected command after roundtrip: {other:?}"),
        }

        Ok(())
    }

    #[test]
    fn request_message_pack_roundtrip_restores_internal_command() -> Result<()> {
        let source_coto = Id::generate();
        let post_to = Id::generate();
        let request = Request {
            id: Uuid::nil(),
            from: None,
            accept: SerializeFormat::MessagePack,
            as_owner: false,
            command: Command::PostSubcoto {
                source_coto,
                input: CotoInput::new("child"),
                post_to: Some(post_to),
                order: Some(4),
            },
        };

        let bytes = to_msgpack_vec_named(&request)?;
        let restored: Request = cotoami_db::rmp_serde::from_slice(&bytes)?;

        match restored.command() {
            Command::PostSubcoto {
                source_coto: actual_source_coto,
                input,
                post_to: actual_post_to,
                order,
            } => {
                assert_eq!(actual_source_coto, source_coto);
                assert_eq!(input.content.as_ref(), "child");
                assert_eq!(actual_post_to, Some(post_to));
                assert_eq!(order, Some(4));
            }
            other => panic!("unexpected command after roundtrip: {other:?}"),
        }

        Ok(())
    }

    #[test]
    fn msgpack_adding_a_defaulted_field_should_remain_backward_compatible() -> Result<()> {
        #[derive(Debug, Serialize)]
        #[serde(tag = "type", rename_all = "snake_case")]
        enum CommandSchemaV1 {
            SetImageMaxSize { size: i32 },
        }

        #[derive(Debug, Deserialize)]
        #[serde(tag = "type", rename_all = "snake_case")]
        enum CommandSchemaV2 {
            SetImageMaxSize {
                #[serde(default)]
                unit: Option<String>,
                size: i32,
            },
        }

        let bytes = to_msgpack_vec_named(&CommandSchemaV1::SetImageMaxSize { size: 1024 })?;

        let restored: CommandSchemaV2 = cotoami_db::rmp_serde::from_slice(&bytes)?;
        match restored {
            CommandSchemaV2::SetImageMaxSize { size, unit } => {
                assert_eq!(size, 1024);
                assert_eq!(unit, None);
            }
        }

        Ok(())
    }

    #[test]
    fn msgpack_reordering_fields_should_not_break_compatibility() -> Result<()> {
        #[derive(Debug, Serialize)]
        #[serde(tag = "type", rename_all = "snake_case")]
        enum CommandSchemaV1 {
            SearchCotos { query: String, only_cotonomas: bool },
        }

        #[derive(Debug, Deserialize)]
        #[serde(tag = "type", rename_all = "snake_case")]
        enum CommandSchemaV2 {
            SearchCotos { only_cotonomas: bool, query: String },
        }

        let bytes = to_msgpack_vec_named(&CommandSchemaV1::SearchCotos {
            query: "rust".into(),
            only_cotonomas: true,
        })?;

        let restored: CommandSchemaV2 = cotoami_db::rmp_serde::from_slice(&bytes)?;
        match restored {
            CommandSchemaV2::SearchCotos {
                only_cotonomas,
                query,
            } => {
                assert!(only_cotonomas);
                assert_eq!(query, "rust");
            }
        }

        Ok(())
    }
}
