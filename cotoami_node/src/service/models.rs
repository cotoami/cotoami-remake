use chrono::NaiveDateTime;
use cotoami_db::prelude::*;
use derive_new::new;
use validator::Validate;

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
// Session
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct CreateClientNodeSession {
    pub password: String,
    pub new_password: Option<String>,
    pub client: Node,
    pub as_parent: Option<bool>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct Session {
    pub token: String,
    pub expires_at: NaiveDateTime, // UTC
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct ClientNodeSession {
    pub session: Session,
    pub server: Node,
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
// Server
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, Clone, serde::Serialize)]
#[serde(tag = "reason", content = "details")]
pub enum NotConnected {
    Disabled,
    Connecting(Option<String>),
    InitFailed(String),
    Disconnected(Option<String>),
}

/////////////////////////////////////////////////////////////////////////////
// Coto / Cotonoma
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, serde::Serialize, serde::Deserialize, new)]
pub struct CotonomaDetails {
    pub cotonoma: Cotonoma,
    pub coto: Coto,
    pub supers: Vec<Cotonoma>,
    pub subs: Paginated<Cotonoma>,
}

#[derive(Debug, serde::Serialize, serde::Deserialize, new)]
pub struct PaginatedCotos {
    pub page: Paginated<Coto>,
    pub related_data: CotosRelatedData,
}

#[derive(Debug, serde::Serialize, serde::Deserialize, new)]
pub struct CotosRelatedData {
    pub posted_in: Vec<Cotonoma>,
    pub as_cotonomas: Vec<Cotonoma>,
    pub originals: Vec<Coto>,
}
