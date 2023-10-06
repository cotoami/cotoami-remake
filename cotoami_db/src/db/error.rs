//! Errors related to database operations

use std::path::PathBuf;

use thiserror::Error;

use crate::models::{node::Node, Id};

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

    #[error("Permission denied")]
    PermissionDenied,

    #[error(
        "Unexpected change number (expected {expected}, actual {actual}) from {parent_node_id}"
    )]
    UnexpectedChangeNumber {
        expected: i64,
        actual: i64,
        parent_node_id: Id<Node>,
    },

    #[error("Change number out of range: {number} (max: {max})")]
    ChangeNumberOutOfRange { number: i64, max: i64 },

    #[error("The local node has already been forked from: {parent_node_id}")]
    AlreadyForkedFromParent { parent_node_id: Id<Node> },
}

impl DatabaseError {
    pub fn not_found(kind: EntityKind, id: impl Into<String>) -> Self {
        DatabaseError::EntityNotFound {
            kind,
            id: id.into(),
        }
    }
}

#[derive(Debug, derive_more::Display)]
pub enum EntityKind {
    #[display("node")]
    Node,
    #[display("local_node")]
    LocalNode,
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
