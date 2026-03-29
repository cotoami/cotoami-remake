//! Dedicated schema for `Command`.
//!
//! `Command` is the core service-domain enum and is likely to evolve for
//! implementation reasons. This schema freezes the transport contract in terms
//! of explicit variant names. Nested payload structs currently reuse the
//! internal service types directly, and can be split into dedicated schemas
//! later if their wire representation ever needs to diverge.

use cotoami_db::prelude::*;
use serde::{Deserialize, Serialize};

use crate::service::{models::*, Command};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum CommandSchema {
    LocalNode,
    LocalServer,
    SetLocalNodeIcon { icon: Bytes },
    SetImageMaxSize { size: i32 },
    EnableAnonymousRead { enable: bool },
    InitialDataset,
    ChunkOfChanges { from: i64 },
    NodeDetails { id: Id<Node> },
    CreateClientNodeSession { session: CreateClientNodeSession },
    TryLogIntoServer { login: LogIntoServer },
    AddServer { server: LogIntoServer },
    EditServer { id: Id<Node>, values: EditServer },
    RecentClients { pagination: Pagination },
    ClientNode { id: Id<Node> },
    AddClient { client: AddClient },
    ResetClientPassword { id: Id<Node> },
    EditClient { id: Id<Node>, values: EditClient },
    ChildNode { id: Id<Node> },
    EditChild { id: Id<Node>, values: ChildNodeInput },
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
    Cotonoma { id: Id<Cotonoma> },
    CotonomaDetails { id: Id<Cotonoma> },
    CotonomaByCotoId { id: Id<Coto> },
    CotonomaByName { name: String, node: Id<Node> },
    SubCotonomas {
        id: Id<Cotonoma>,
        pagination: Pagination,
    },
    RecentCotos {
        scope: Scope,
        only_cotonomas: bool,
        pagination: Pagination,
    },
    GeolocatedCotos { scope: Scope },
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
    CotoDetails { id: Id<Coto> },
    GraphFromCoto { coto: Id<Coto> },
    GraphFromCotonoma { cotonoma: Id<Cotonoma> },
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
    Promote { id: Id<Coto> },
    DeleteCoto { id: Id<Coto> },
    Repost { id: Id<Coto>, dest: Id<Cotonoma> },
    RenameCotonoma { id: Id<Cotonoma>, name: String },
    Ito { id: Id<Ito> },
    SiblingItos {
        coto: Id<Coto>,
        #[serde(default)]
        node: Option<Id<Node>>,
    },
    CreateIto { input: ItoInput<'static> },
    EditIto {
        id: Id<Ito>,
        diff: ItoContentDiff<'static>,
    },
    DeleteIto { id: Id<Ito> },
    ChangeItoOrder { id: Id<Ito>, new_order: i32 },
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
            Command::RecentCotonomas { node, pagination } => Self::RecentCotonomas { node, pagination },
            Command::CotonomasByPrefix { prefix, nodes } => Self::CotonomasByPrefix { prefix, nodes },
            Command::CotonomasByPartial { partial, nodes } => Self::CotonomasByPartial { partial, nodes },
            Command::Cotonoma { id } => Self::Cotonoma { id },
            Command::CotonomaDetails { id } => Self::CotonomaDetails { id },
            Command::CotonomaByCotoId { id } => Self::CotonomaByCotoId { id },
            Command::CotonomaByName { name, node } => Self::CotonomaByName { name, node },
            Command::SubCotonomas { id, pagination } => Self::SubCotonomas { id, pagination },
            Command::RecentCotos { scope, only_cotonomas, pagination } => Self::RecentCotos {
                scope,
                only_cotonomas,
                pagination,
            },
            Command::GeolocatedCotos { scope } => Self::GeolocatedCotos { scope },
            Command::CotosInGeoBounds { southwest, northeast } => {
                Self::CotosInGeoBounds { southwest, northeast }
            }
            Command::SearchCotos { query, scope, only_cotonomas, pagination } => Self::SearchCotos {
                query,
                scope,
                only_cotonomas,
                pagination,
            },
            Command::CotoDetails { id } => Self::CotoDetails { id },
            Command::GraphFromCoto { coto } => Self::GraphFromCoto { coto },
            Command::GraphFromCotonoma { cotonoma } => Self::GraphFromCotonoma { cotonoma },
            Command::PostCoto { input, post_to } => Self::PostCoto { input, post_to },
            Command::PostCotonoma { input, post_to } => {
                Self::PostCotonoma { input, post_to }
            }
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
            Command::PostSubcoto { source_coto, input, post_to, order } => Self::PostSubcoto {
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
            CommandSchema::CreateClientNodeSession { session } => Self::CreateClientNodeSession(session),
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
            CommandSchema::RecentCotonomas { node, pagination } => Self::RecentCotonomas { node, pagination },
            CommandSchema::CotonomasByPrefix { prefix, nodes } => Self::CotonomasByPrefix { prefix, nodes },
            CommandSchema::CotonomasByPartial { partial, nodes } => Self::CotonomasByPartial { partial, nodes },
            CommandSchema::Cotonoma { id } => Self::Cotonoma { id },
            CommandSchema::CotonomaDetails { id } => Self::CotonomaDetails { id },
            CommandSchema::CotonomaByCotoId { id } => Self::CotonomaByCotoId { id },
            CommandSchema::CotonomaByName { name, node } => Self::CotonomaByName { name, node },
            CommandSchema::SubCotonomas { id, pagination } => Self::SubCotonomas { id, pagination },
            CommandSchema::RecentCotos { scope, only_cotonomas, pagination } => Self::RecentCotos {
                scope,
                only_cotonomas,
                pagination,
            },
            CommandSchema::GeolocatedCotos { scope } => Self::GeolocatedCotos { scope },
            CommandSchema::CotosInGeoBounds { southwest, northeast } => {
                Self::CotosInGeoBounds { southwest, northeast }
            }
            CommandSchema::SearchCotos { query, scope, only_cotonomas, pagination } => Self::SearchCotos {
                query,
                scope,
                only_cotonomas,
                pagination,
            },
            CommandSchema::CotoDetails { id } => Self::CotoDetails { id },
            CommandSchema::GraphFromCoto { coto } => Self::GraphFromCoto { coto },
            CommandSchema::GraphFromCotonoma { cotonoma } => Self::GraphFromCotonoma { cotonoma },
            CommandSchema::PostCoto { input, post_to } => Self::PostCoto { input, post_to },
            CommandSchema::PostCotonoma { input, post_to } => {
                Self::PostCotonoma { input, post_to }
            }
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
            CommandSchema::ChangeItoOrder { id, new_order } => Self::ChangeItoOrder { id, new_order },
            CommandSchema::OthersLastPostedAt => Self::OthersLastPostedAt,
            CommandSchema::MarkAsRead { node } => Self::MarkAsRead { node },
            CommandSchema::PostSubcoto { source_coto, input, post_to, order } => Self::PostSubcoto {
                source_coto,
                input,
                post_to,
                order,
            },
        }
    }
}
