//! Service models represent inputs or outputs of service implementations.
//!
//! An instance of a model struct is passed to services via [super::Command] or
//! serialized into a body of a response ([super::Response::body]).

use anyhow::Result;
use chrono::NaiveDateTime;
use cotoami_db::prelude::*;
use derive_new::new;
use itertools::Itertools;
use validator::Validate;

use crate::config::ServerConfig;

/////////////////////////////////////////////////////////////////////////////
// Pagination
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, Validate)]
pub struct Pagination {
    #[serde(default)]
    pub page: i64,

    #[validate(range(min = 1, max = 1000))]
    pub page_size: Option<i64>,
}

/////////////////////////////////////////////////////////////////////////////
// LocalServer
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct LocalServer {
    pub active_config: Option<ServerConfig>,
    pub anonymous_read_enabled: bool,
    pub anonymous_connections: usize,
}

/////////////////////////////////////////////////////////////////////////////
// Session
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Copy, Clone, serde::Serialize, serde::Deserialize)]
pub enum NodeRole {
    Parent,
    Child,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct CreateClientNodeSession {
    pub password: Option<String>, // None for anonymous access
    pub new_password: Option<String>,
    pub client: Node,
    pub client_role: Option<NodeRole>,
}

impl CreateClientNodeSession {
    pub fn client_role(&self) -> NodeRole { self.client_role.unwrap_or(NodeRole::Child) }
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct SessionToken {
    pub token: String,
    pub expires_at: NaiveDateTime, // UTC
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct ClientNodeSession {
    pub token: Option<SessionToken>, // None for a anonymous client
    pub server: Node,
    pub server_root: Option<(Cotonoma, Coto)>,
    pub child_privileges: Option<ChildNode>,
}

impl ClientNodeSession {
    pub fn new_server_role(&self) -> NewDatabaseRole {
        match (&self.token, &self.child_privileges) {
            (Some(_), Some(_)) => NewDatabaseRole::Parent,
            (Some(_), None) => NewDatabaseRole::Child {
                as_owner: false,
                can_edit_itos: false,
            },
            (None, _) => NewDatabaseRole::Parent, // anonymous access
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Client
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, Validate)]
pub struct AddClient {
    #[validate(required)]
    pub id: Option<Id<Node>>,
    pub password: Option<String>, // if None, the password will be auto-generated
    pub client_role: Option<NodeRole>, // the default values is: NodeRole::Child

    // Client as a child
    pub as_owner: Option<bool>,
    pub can_edit_itos: Option<bool>,
}

impl AddClient {
    pub fn new(id: Id<Node>, role: NodeRole, password: Option<impl Into<String>>) -> Self {
        Self {
            id: Some(id),
            password: password.map(Into::into),
            client_role: Some(role),
            as_owner: None,
            can_edit_itos: None,
        }
    }

    pub fn client_role(&self) -> NodeRole { self.client_role.unwrap_or(NodeRole::Child) }

    pub fn as_owner(&self) -> bool { self.as_owner.unwrap_or(false) }

    pub fn can_edit_itos(&self) -> bool { self.can_edit_itos.unwrap_or(false) }
}

#[derive(Debug, serde::Serialize, serde::Deserialize)]
pub struct ClientAdded {
    /// Generated password
    pub password: String,
    pub client: ClientNode,
    pub node: Node,
}

/////////////////////////////////////////////////////////////////////////////
// Database
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, serde::Serialize, serde::Deserialize)]
pub struct InitialDataset {
    pub last_change_number: i64,
    pub nodes: Vec<Node>,
    pub local_node_id: Id<Node>,
    pub parent_node_ids: Vec<Id<Node>>,
    pub servers: Vec<Server>,
    pub active_clients: Vec<ActiveClient>,
}

impl InitialDataset {
    pub fn local_node(&self) -> Option<&Node> { self.node(&self.local_node_id) }

    pub fn node(&self, id: &Id<Node>) -> Option<&Node> {
        self.nodes.iter().find(|node| node.uuid == *id)
    }
}

/////////////////////////////////////////////////////////////////////////////
// Node
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, serde::Serialize, serde::Deserialize, new)]
pub struct NodeDetails {
    pub node: Node,
    pub root: Option<(Cotonoma, Coto)>,
}

/////////////////////////////////////////////////////////////////////////////
// Server
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, Validate)]
pub struct LogIntoServer {
    #[validate(required, url)]
    pub url_prefix: Option<String>,

    pub password: Option<String>, // None for anonymous access

    pub new_password: Option<String>,

    pub client_role: Option<NodeRole>,
}

impl LogIntoServer {
    pub fn into_session_request(self, client: Node) -> Result<CreateClientNodeSession> {
        self.validate()?;
        Ok(CreateClientNodeSession {
            password: self.password,
            new_password: self.new_password,
            client,
            client_role: self.client_role,
        })
    }
}

#[derive(Debug, serde::Serialize, serde::Deserialize, new)]
pub struct Server {
    pub server: ServerNode,
    pub role: Option<DatabaseRole>,
    pub not_connected: Option<NotConnected>,
    pub child_privileges: Option<ChildNode>,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[serde(tag = "reason", content = "details")]
pub enum NotConnected {
    Disabled,
    Connecting(Option<String>),
    InitFailed(String),
    Disconnected(Option<String>),
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, Validate)]
pub struct EditServer {
    pub disabled: Option<bool>,
    pub url_prefix: Option<String>,
}

/////////////////////////////////////////////////////////////////////////////
// Client
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize, new)]
pub struct ActiveClient {
    pub node_id: Id<Node>,
    pub remote_addr: String,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, Validate)]
pub struct EditClient {
    pub disabled: Option<bool>,
}

/////////////////////////////////////////////////////////////////////////////
// Child
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, Validate)]
pub struct EditChild {
    pub as_owner: bool,
    pub can_edit_itos: bool,
}

/////////////////////////////////////////////////////////////////////////////
// Changes
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub enum ChunkOfChanges {
    Fetched(Changes),
    OutOfRange { max: i64 },
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct Changes {
    pub chunk: Vec<ChangelogEntry>,
    pub last_serial_number: i64,
}

impl Changes {
    pub fn last_serial_number_of_chunk(&self) -> i64 {
        self.chunk.last().map(|c| c.serial_number).unwrap_or(0)
    }

    pub fn is_last_chunk(&self) -> bool {
        if let Some(change) = self.chunk.last() {
            // For safety's sake (to avoid infinite loop), leave it as the last chunk
            // if the last serial number of it is equal **or larger than** the
            // last serial number of all, rather than exactly the same number.
            change.serial_number >= self.last_serial_number
        } else {
            true // empty (no changes) means the last chunk
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Cotonoma
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, serde::Serialize, serde::Deserialize, new)]
pub struct CotonomaDetails {
    pub cotonoma: Cotonoma,
    pub coto: Coto,
    pub supers: Vec<Cotonoma>,
    pub subs: Page<Cotonoma>,
}

/////////////////////////////////////////////////////////////////////////////
// Coto
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, serde::Serialize, serde::Deserialize, new)]
pub struct CotoDetails {
    pub coto: Coto,
    pub related_data: CotosRelatedData,
    pub outgoing_itos: Vec<Ito>,
}

#[derive(Debug, serde::Serialize, serde::Deserialize)]
pub struct PaginatedCotos {
    pub page: Page<Coto>,
    pub related_data: CotosRelatedData,
    pub outgoing_itos: Vec<Ito>,
}

impl PaginatedCotos {
    pub(crate) fn new(page: Page<Coto>, ds: &mut DatabaseSession<'_>) -> Result<Self> {
        let related_data = CotosRelatedData::fetch(ds, &page.rows)?;

        // Collect the itos from the cotos
        // (as for reposts, collect the itos from the original coto)
        let coto_ids: Vec<Id<Coto>> = page
            .rows
            .iter()
            .map(|coto| {
                if coto.is_repost() {
                    None
                } else {
                    Some(coto.uuid)
                }
            })
            .flatten()
            .chain(related_data.originals.iter().map(|coto| coto.uuid))
            .unique()
            .collect();
        let outgoing_itos = ds.outgoing_itos(&coto_ids)?;

        Ok(PaginatedCotos {
            page,
            related_data,
            outgoing_itos,
        })
    }
}

#[derive(Debug, serde::Serialize, serde::Deserialize)]
pub struct GeolocatedCotos {
    pub cotos: Vec<Coto>,
    pub related_data: CotosRelatedData,
}

impl GeolocatedCotos {
    pub(crate) fn new(cotos: Vec<Coto>, ds: &mut DatabaseSession<'_>) -> Result<Self> {
        let related_data = CotosRelatedData::fetch(ds, &cotos)?;
        Ok(GeolocatedCotos {
            cotos,
            related_data,
        })
    }
}

#[derive(Debug, serde::Serialize, serde::Deserialize, new)]
pub struct CotosRelatedData {
    pub posted_in: Vec<Cotonoma>,
    pub as_cotonomas: Vec<Cotonoma>,
    pub originals: Vec<Coto>,
}

impl CotosRelatedData {
    pub(crate) fn fetch(ds: &mut DatabaseSession<'_>, cotos: &[Coto]) -> Result<Self> {
        let original_ids: Vec<Id<Coto>> =
            cotos.iter().filter_map(|coto| coto.repost_of_id).collect();
        let originals = ds.cotos(&original_ids)?;
        let posted_in = ds.cotonomas_of(cotos.iter().chain(originals.iter()))?;
        let as_cotonomas = ds.as_cotonomas(cotos.iter())?;
        Ok(Self::new(posted_in, as_cotonomas, originals))
    }
}

#[derive(Debug, serde::Serialize, serde::Deserialize, new)]
pub struct CotoGraph {
    pub root_coto_id: Id<Coto>,
    pub root_cotonoma: Option<Cotonoma>,
    pub cotos: Vec<Coto>,
    pub cotos_related_data: CotosRelatedData,
    pub itos: Vec<Ito>,
}
