use cotoami_db::prelude::*;

use crate::service::models::*;

#[derive(derive_more::Debug, Clone, serde::Serialize, serde::Deserialize)]
pub enum Command {
    /// Request the local node as a [Node].
    LocalNode,

    /// Request an [InitialDataset].
    InitialDataset,

    /// Request a [ChunkOfChanges] from a change number `from`.
    ChunkOfChanges { from: i64 },

    /// Request to change the icon of the local node and and return a [Node] if succeeded.
    SetLocalNodeIcon {
        #[debug(skip)]
        icon: Bytes,
    },

    /// Request a [NodeDetails] of the given ID.
    NodeDetails { id: Id<Node> },

    /// Request a new [ClientNodeSession] on the local node.
    CreateClientNodeSession(CreateClientNodeSession),

    /// Request to log into the server node and return a [ClientNodeSession] if succeeded.
    TryLogIntoServer(LogIntoServer),

    /// Request to add a new [Server].
    AddServer(LogIntoServer),

    /// Request to edit a server node and return the updated [ServerNode].
    EditServer { id: Id<Node>, values: EditServer },

    /// Request a [Page<ClientNode>] that contains recently registered clients.
    RecentClients { pagination: Pagination },

    /// Request a [ClientNode] of the given node ID.
    ClientNode { id: Id<Node> },

    /// Request to add a new client node and return [ClientAdded] if succeeded.
    AddClient(AddClient),

    /// Request to edit a client node and return the updated [ClientNode].
    EditClient { id: Id<Node>, values: EditClient },

    /// Request a [Page<Cotonoma>] that contains recently updated cotonomas.
    RecentCotonomas {
        node: Option<Id<Node>>,
        pagination: Pagination,
    },

    /// Request a [Vec<Cotonoma>] in `target_nodes` whose name start with the given `prefix`.
    CotonomasByPrefix {
        prefix: String,
        nodes: Option<Vec<Id<Node>>>,
    },

    /// Request a tuple of [Cotonoma] and [Coto] `(Cotonoma, Coto)` of the given cotonoma ID.
    Cotonoma { id: Id<Cotonoma> },

    /// Request a [CotonomaDetails] of the given ID.
    CotonomaDetails { id: Id<Cotonoma> },

    /// Request a [Cotonoma] of the given name in the given node.
    CotonomaByName { name: String, node: Id<Node> },

    /// Request a [Page<Cotonoma>] that contains sub cotonomas of the given cotonoma.
    SubCotonomas {
        id: Id<Cotonoma>,
        pagination: Pagination,
    },

    /// Request [CotosPage] that contains recently posted cotos.
    RecentCotos {
        node: Option<Id<Node>>,
        cotonoma: Option<Id<Cotonoma>>,
        only_cotonomas: bool,
        pagination: Pagination,
    },

    /// Request [GeolocatedCotos] in the given node or cotonoma.
    GeolocatedCotos {
        node: Option<Id<Node>>,
        cotonoma: Option<Id<Cotonoma>>,
    },

    /// Request [GeolocatedCotos] found in the given geo bounds.
    CotosInGeoBounds {
        southwest: Geolocation,
        northeast: Geolocation,
    },

    /// Request [CotosPage] that match the given query and cotonoma.
    SearchCotos {
        query: String,
        node: Option<Id<Node>>,
        cotonoma: Option<Id<Cotonoma>>,
        only_cotonomas: bool,
        pagination: Pagination,
    },

    /// Request a [CotoDetails] of the given ID.
    CotoDetails { id: Id<Coto> },

    /// Request a [CotoGraph] by traversing from the given coto.
    GraphFromCoto { coto: Id<Coto> },

    /// Request a [CotoGraph] by traversing from the given cotonoma.
    GraphFromCotonoma { cotonoma: Id<Cotonoma> },

    /// Request to create a new [Coto] in the given cotonoma (`post_to`),
    /// and return the [Coto] if suceeded.
    PostCoto {
        input: CotoInput<'static>,
        post_to: Id<Cotonoma>,
    },

    /// Request to create a new [Cotonoma] in the given cotonoma (`post_to`).
    /// The return type is a tuple of [Cotonoma] and [Coto] `(Cotonoma, Coto)`.
    PostCotonoma {
        input: CotonomaInput<'static>,
        post_to: Id<Cotonoma>,
    },

    /// Request to edit the specified coto and return the updated [Coto] if suceeded.
    EditCoto {
        id: Id<Coto>,
        diff: CotoContentDiff<'static>,
    },

    /// Request to delete a coto and return the [Id<Coto>] if suceeded.
    DeleteCoto { id: Id<Coto> },

    /// Request to repost a coto to the dest cotonoma and
    /// return the pair of the repost and the original coto: `(Coto, Coto)`.
    Repost { id: Id<Coto>, dest: Id<Cotonoma> },

    /// Request to rename the specified [Cotonoma] to the given `name`.
    /// The return type is a tuple of [Cotonoma] and [Coto] `(Cotonoma, Coto)`.
    RenameCotonoma { id: Id<Cotonoma>, name: String },

    /// Request to create a new [Link] and return the [Link] if suceeded.
    Connect(LinkInput<'static>),

    /// Request to edit the specified link and return the updated [Link] if suceeded.
    EditLink {
        id: Id<Link>,
        diff: LinkContentDiff<'static>,
    },

    /// Request to delete a link and return the [Id<Link>] if suceeded.
    Disconnect { id: Id<Link> },
}
