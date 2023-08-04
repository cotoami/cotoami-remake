//! Errors related to database operations

use std::path::PathBuf;

use thiserror::Error;

#[derive(Error, Debug)]
pub enum DatabaseError {
    #[error("Invalid directory path: {0}")]
    InvalidRootDir(PathBuf),

    #[error("Invalid file path: {path} ({reason})")]
    InvalidFilePath { path: PathBuf, reason: String },

    #[error("Unexpected change number (expected {expected}, actual {actual})")]
    UnexpectedChangeNumber { expected: i64, actual: i64 },

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

#[derive(strum_macros::Display, Debug)]
pub enum EntityKind {
    #[strum(serialize = "child_node")]
    ChildNode,
    #[strum(serialize = "coto")]
    Coto,
    #[strum(serialize = "cotonoma")]
    Cotonoma,
    #[strum(serialize = "link")]
    Link,
}

#[derive(strum_macros::Display, Debug)]
pub enum OpKind {
    #[strum(serialize = "create")]
    Create,
    #[strum(serialize = "read")]
    Read,
    #[strum(serialize = "update")]
    Update,
    #[strum(serialize = "delete")]
    Delete,
}
