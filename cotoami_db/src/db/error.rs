//! Errors related to database operations

use std::path::PathBuf;

use thiserror::Error;

#[derive(Error, Debug)]
pub enum DatabaseError {
    #[error("Invalid directory path: {0}")]
    InvalidRootDir(PathBuf),

    #[error("Invalid file path: {path} ({reason})")]
    InvalidFilePath { path: PathBuf, reason: String },

    #[error("Local node has not yet been created")]
    LocalNodeNotYetInitialized,

    #[error("Not found: {kind} ({id})")]
    EntityNotFound { kind: EntityKind, id: String },

    #[error("Authentication failed")]
    AuthenticationFailed,

    #[error("Permission denied: {entity} ({id:?}) - {op}")]
    PermissionDenied {
        entity: EntityKind,
        id: Option<String>,
        op: OpKind,
    },

    #[error("Unexpected change number (expected {expected:?}, actual {actual:?}) from {parent_node_id:?}")]
    UnexpectedChangeNumber {
        expected: i64,
        actual: i64,
        parent_node_id: String,
    },

    #[error("Change number out of range: {number} (max: {max})")]
    ChangeNumberOutOfRange { number: i64, max: i64 },
}

impl DatabaseError {
    pub fn not_found(kind: EntityKind, id: impl Into<String>) -> Self {
        DatabaseError::EntityNotFound {
            kind,
            id: id.into(),
        }
    }

    pub fn permission_denied(
        entity: EntityKind,
        id: Option<impl Into<String>>,
        op: OpKind,
    ) -> Self {
        DatabaseError::PermissionDenied {
            entity,
            id: id.map(Into::into),
            op,
        }
    }
}

#[derive(Debug, derive_more::Display)]
pub enum EntityKind {
    #[display("node")]
    Node,
    #[display("parent_node")]
    ParentNode,
    #[display("child_node")]
    ChildNode,
    #[display("coto")]
    Coto,
    #[display("cotonoma")]
    Cotonoma,
    #[display("link")]
    Link,
}

#[derive(Debug, derive_more::Display)]
pub enum OpKind {
    #[display("create")]
    Create,
    #[display("read")]
    Read,
    #[display("update")]
    Update,
    #[display("delete")]
    Delete,
}
