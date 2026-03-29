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
//! In practice, [`CommandSchema`] is the stable schema for the command payload,
//! while the private request envelope schema in this module controls how a
//! [`super::Request`] is represented on the wire. Conversion code between the
//! schema types and the internal service types is part of the protocol layer,
//! not part of the domain model.
//!
//! The schema types in this module prefer:
//!
//! - explicit variant tags and field names
//! - struct-like payloads instead of positional tuple forms
//! - owned data where transport safety is more important than borrowing
//! - `Option`/default-based tolerance where additive evolution is expected
//!
//! For MessagePack specifically, these schemas are intended to be serialized
//! with named struct fields via [`crate::codec::to_msgpack_vec_named`] so that
//! field-order changes and additive fields can evolve more safely than with the
//! default compact positional encoding.
//!
use std::borrow::Cow;

use cotoami_db::{models::DateTimeRange, prelude::*};
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use uuid::Uuid;

use crate::service::{models::*, Command, Request, SerializeFormat};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum CommandSchema {
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
        scope: ScopeSchema,
        only_cotonomas: bool,
        pagination: Pagination,
    },
    GeolocatedCotos {
        scope: ScopeSchema,
    },
    CotosInGeoBounds {
        southwest: Geolocation,
        northeast: Geolocation,
    },
    SearchCotos {
        query: String,
        scope: ScopeSchema,
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
        input: CotoInputSchema,
        post_to: Id<Cotonoma>,
    },
    PostCotonoma {
        input: CotonomaInputSchema,
        post_to: Id<Cotonoma>,
    },
    EditCoto {
        id: Id<Coto>,
        diff: CotoContentDiffSchema,
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
        input: ItoInputSchema,
    },
    EditIto {
        id: Id<Ito>,
        diff: ItoContentDiffSchema,
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
        input: CotoInputSchema,
        #[serde(default)]
        post_to: Option<Id<Cotonoma>>,
        #[serde(default)]
        order: Option<i32>,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ScopeSchema {
    All,
    Node {
        node_id: Id<Node>,
    },
    Cotonoma {
        cotonoma_id: Id<Cotonoma>,
        scope: CotonomaScopeSchema,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum CotonomaScopeSchema {
    Local,
    Recursive,
    Depth { depth: usize },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MediaContentSchema {
    pub content: Bytes,
    pub media_type: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CotoInputSchema {
    pub content: String,
    #[serde(default)]
    pub summary: Option<String>,
    #[serde(default)]
    pub media_content: Option<MediaContentSchema>,
    #[serde(default)]
    pub geolocation: Option<Geolocation>,
    #[serde(default)]
    pub datetime_range: Option<DateTimeRange>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CotonomaInputSchema {
    pub name: String,
    #[serde(default)]
    pub geolocation: Option<Geolocation>,
    #[serde(default)]
    pub datetime_range: Option<DateTimeRange>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ItoInputSchema {
    pub source_coto_id: Id<Coto>,
    pub target_coto_id: Id<Coto>,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub details: Option<String>,
    #[serde(default)]
    pub order: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum FieldDiffSchema<T> {
    None,
    Delete,
    Change { value: T },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CotoContentDiffSchema {
    pub content: FieldDiffSchema<String>,
    pub summary: FieldDiffSchema<String>,
    pub media_content: FieldDiffSchema<MediaContentSchema>,
    pub geolocation: FieldDiffSchema<Geolocation>,
    pub datetime_range: FieldDiffSchema<DateTimeRange>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ItoContentDiffSchema {
    pub description: FieldDiffSchema<String>,
    pub details: FieldDiffSchema<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct RequestSchema {
    id: Uuid,
    accept: SerializeFormat,
    #[serde(default)]
    as_owner: bool,
    command: CommandSchema,
}

impl Serialize for Request {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        RequestSchema::from(self).serialize(serializer)
    }
}

impl<'de> Deserialize<'de> for Request {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        RequestSchema::deserialize(deserializer).map(Into::into)
    }
}

impl From<&Request> for RequestSchema {
    fn from(request: &Request) -> Self {
        Self {
            id: request.id,
            accept: request.accept,
            as_owner: request.as_owner,
            command: request.command.clone().into(),
        }
    }
}

impl From<RequestSchema> for Request {
    fn from(wire: RequestSchema) -> Self {
        Self {
            id: wire.id,
            from: None,
            accept: wire.accept,
            as_owner: wire.as_owner,
            command: wire.command.into(),
        }
    }
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
                scope: scope.into(),
                only_cotonomas,
                pagination,
            },
            Command::GeolocatedCotos { scope } => Self::GeolocatedCotos {
                scope: scope.into(),
            },
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
                scope: scope.into(),
                only_cotonomas,
                pagination,
            },
            Command::CotoDetails { id } => Self::CotoDetails { id },
            Command::GraphFromCoto { coto } => Self::GraphFromCoto { coto },
            Command::GraphFromCotonoma { cotonoma } => Self::GraphFromCotonoma { cotonoma },
            Command::PostCoto { input, post_to } => Self::PostCoto {
                input: input.into(),
                post_to,
            },
            Command::PostCotonoma { input, post_to } => Self::PostCotonoma {
                input: input.into(),
                post_to,
            },
            Command::EditCoto { id, diff } => Self::EditCoto {
                id,
                diff: diff.into(),
            },
            Command::Promote { id } => Self::Promote { id },
            Command::DeleteCoto { id } => Self::DeleteCoto { id },
            Command::Repost { id, dest } => Self::Repost { id, dest },
            Command::RenameCotonoma { id, name } => Self::RenameCotonoma { id, name },
            Command::Ito { id } => Self::Ito { id },
            Command::SiblingItos { coto, node } => Self::SiblingItos { coto, node },
            Command::CreateIto(input) => Self::CreateIto {
                input: input.into(),
            },
            Command::EditIto { id, diff } => Self::EditIto {
                id,
                diff: diff.into(),
            },
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
                input: input.into(),
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
                scope: scope.into(),
                only_cotonomas,
                pagination,
            },
            CommandSchema::GeolocatedCotos { scope } => Self::GeolocatedCotos {
                scope: scope.into(),
            },
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
                scope: scope.into(),
                only_cotonomas,
                pagination,
            },
            CommandSchema::CotoDetails { id } => Self::CotoDetails { id },
            CommandSchema::GraphFromCoto { coto } => Self::GraphFromCoto { coto },
            CommandSchema::GraphFromCotonoma { cotonoma } => Self::GraphFromCotonoma { cotonoma },
            CommandSchema::PostCoto { input, post_to } => Self::PostCoto {
                input: input.into(),
                post_to,
            },
            CommandSchema::PostCotonoma { input, post_to } => Self::PostCotonoma {
                input: input.into(),
                post_to,
            },
            CommandSchema::EditCoto { id, diff } => Self::EditCoto {
                id,
                diff: diff.into(),
            },
            CommandSchema::Promote { id } => Self::Promote { id },
            CommandSchema::DeleteCoto { id } => Self::DeleteCoto { id },
            CommandSchema::Repost { id, dest } => Self::Repost { id, dest },
            CommandSchema::RenameCotonoma { id, name } => Self::RenameCotonoma { id, name },
            CommandSchema::Ito { id } => Self::Ito { id },
            CommandSchema::SiblingItos { coto, node } => Self::SiblingItos { coto, node },
            CommandSchema::CreateIto { input } => Self::CreateIto(input.into()),
            CommandSchema::EditIto { id, diff } => Self::EditIto {
                id,
                diff: diff.into(),
            },
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
                input: input.into(),
                post_to,
                order,
            },
        }
    }
}

impl From<Scope> for ScopeSchema {
    fn from(scope: Scope) -> Self {
        match scope {
            Scope::All => Self::All,
            Scope::Node(node_id) => Self::Node { node_id },
            Scope::Cotonoma((cotonoma_id, scope)) => Self::Cotonoma {
                cotonoma_id,
                scope: scope.into(),
            },
        }
    }
}

impl From<ScopeSchema> for Scope {
    fn from(scope: ScopeSchema) -> Self {
        match scope {
            ScopeSchema::All => Self::All,
            ScopeSchema::Node { node_id } => Self::Node(node_id),
            ScopeSchema::Cotonoma { cotonoma_id, scope } => {
                Self::Cotonoma((cotonoma_id, scope.into()))
            }
        }
    }
}

impl From<CotonomaScope> for CotonomaScopeSchema {
    fn from(scope: CotonomaScope) -> Self {
        match scope {
            CotonomaScope::Local => Self::Local,
            CotonomaScope::Recursive => Self::Recursive,
            CotonomaScope::Depth(depth) => Self::Depth { depth },
        }
    }
}

impl From<CotonomaScopeSchema> for CotonomaScope {
    fn from(scope: CotonomaScopeSchema) -> Self {
        match scope {
            CotonomaScopeSchema::Local => Self::Local,
            CotonomaScopeSchema::Recursive => Self::Recursive,
            CotonomaScopeSchema::Depth { depth } => Self::Depth(depth),
        }
    }
}

impl From<CotoInput<'static>> for CotoInputSchema {
    fn from(input: CotoInput<'static>) -> Self {
        Self {
            content: input.content.into_owned(),
            summary: input.summary.map(Cow::into_owned),
            media_content: input
                .media_content
                .map(|(content, media_type)| MediaContentSchema {
                    content,
                    media_type: media_type.into_owned(),
                }),
            geolocation: input.geolocation,
            datetime_range: input.datetime_range,
        }
    }
}

impl From<CotoInputSchema> for CotoInput<'static> {
    fn from(input: CotoInputSchema) -> Self {
        Self {
            content: Cow::Owned(input.content),
            summary: input.summary.map(Cow::Owned),
            media_content: input
                .media_content
                .map(|media| (media.content, Cow::Owned(media.media_type))),
            geolocation: input.geolocation,
            datetime_range: input.datetime_range,
        }
    }
}

impl From<CotonomaInput<'static>> for CotonomaInputSchema {
    fn from(input: CotonomaInput<'static>) -> Self {
        Self {
            name: input.name.into_owned(),
            geolocation: input.geolocation,
            datetime_range: input.datetime_range,
        }
    }
}

impl From<CotonomaInputSchema> for CotonomaInput<'static> {
    fn from(input: CotonomaInputSchema) -> Self {
        Self {
            name: Cow::Owned(input.name),
            geolocation: input.geolocation,
            datetime_range: input.datetime_range,
        }
    }
}

impl From<ItoInput<'static>> for ItoInputSchema {
    fn from(input: ItoInput<'static>) -> Self {
        Self {
            source_coto_id: input.source_coto_id,
            target_coto_id: input.target_coto_id,
            description: input.description.map(Cow::into_owned),
            details: input.details.map(Cow::into_owned),
            order: input.order,
        }
    }
}

impl From<ItoInputSchema> for ItoInput<'static> {
    fn from(input: ItoInputSchema) -> Self {
        Self {
            source_coto_id: input.source_coto_id,
            target_coto_id: input.target_coto_id,
            description: input.description.map(Cow::Owned),
            details: input.details.map(Cow::Owned),
            order: input.order,
        }
    }
}

impl From<CotoContentDiff<'static>> for CotoContentDiffSchema {
    fn from(diff: CotoContentDiff<'static>) -> Self {
        Self {
            content: string_field_diff_to_wire(diff.content),
            summary: string_field_diff_to_wire(diff.summary),
            media_content: media_field_diff_to_wire(diff.media_content),
            geolocation: field_diff_to_wire(diff.geolocation),
            datetime_range: field_diff_to_wire(diff.datetime_range),
        }
    }
}

impl From<CotoContentDiffSchema> for CotoContentDiff<'static> {
    fn from(diff: CotoContentDiffSchema) -> Self {
        Self {
            content: string_field_diff_from_wire(diff.content),
            summary: string_field_diff_from_wire(diff.summary),
            media_content: media_field_diff_from_wire(diff.media_content),
            geolocation: field_diff_from_wire(diff.geolocation),
            datetime_range: field_diff_from_wire(diff.datetime_range),
        }
    }
}

impl From<ItoContentDiff<'static>> for ItoContentDiffSchema {
    fn from(diff: ItoContentDiff<'static>) -> Self {
        Self {
            description: string_field_diff_to_wire(diff.description),
            details: string_field_diff_to_wire(diff.details),
        }
    }
}

impl From<ItoContentDiffSchema> for ItoContentDiff<'static> {
    fn from(diff: ItoContentDiffSchema) -> Self {
        Self {
            description: string_field_diff_from_wire(diff.description),
            details: string_field_diff_from_wire(diff.details),
        }
    }
}

fn field_diff_to_wire<T>(diff: FieldDiff<T>) -> FieldDiffSchema<T> {
    match diff {
        FieldDiff::None => FieldDiffSchema::None,
        FieldDiff::Delete => FieldDiffSchema::Delete,
        FieldDiff::Change(value) => FieldDiffSchema::Change { value },
    }
}

fn field_diff_from_wire<T>(diff: FieldDiffSchema<T>) -> FieldDiff<T> {
    match diff {
        FieldDiffSchema::None => FieldDiff::None,
        FieldDiffSchema::Delete => FieldDiff::Delete,
        FieldDiffSchema::Change { value } => FieldDiff::Change(value),
    }
}

fn string_field_diff_to_wire(diff: FieldDiff<Cow<'static, str>>) -> FieldDiffSchema<String> {
    match diff {
        FieldDiff::None => FieldDiffSchema::None,
        FieldDiff::Delete => FieldDiffSchema::Delete,
        FieldDiff::Change(value) => FieldDiffSchema::Change {
            value: value.into_owned(),
        },
    }
}

fn string_field_diff_from_wire(diff: FieldDiffSchema<String>) -> FieldDiff<Cow<'static, str>> {
    match diff {
        FieldDiffSchema::None => FieldDiff::None,
        FieldDiffSchema::Delete => FieldDiff::Delete,
        FieldDiffSchema::Change { value } => FieldDiff::Change(Cow::Owned(value)),
    }
}

fn media_field_diff_to_wire(
    diff: FieldDiff<(Bytes, Cow<'static, str>)>,
) -> FieldDiffSchema<MediaContentSchema> {
    match diff {
        FieldDiff::None => FieldDiffSchema::None,
        FieldDiff::Delete => FieldDiffSchema::Delete,
        FieldDiff::Change((content, media_type)) => FieldDiffSchema::Change {
            value: MediaContentSchema {
                content,
                media_type: media_type.into_owned(),
            },
        },
    }
}

fn media_field_diff_from_wire(
    diff: FieldDiffSchema<MediaContentSchema>,
) -> FieldDiff<(Bytes, Cow<'static, str>)> {
    match diff {
        FieldDiffSchema::None => FieldDiff::None,
        FieldDiffSchema::Delete => FieldDiff::Delete,
        FieldDiffSchema::Change { value } => {
            FieldDiff::Change((value.content, Cow::Owned(value.media_type)))
        }
    }
}

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use serde_json::json;

    use super::*;

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

        let bytes = crate::codec::to_msgpack_vec_named(&request)?;
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

}
