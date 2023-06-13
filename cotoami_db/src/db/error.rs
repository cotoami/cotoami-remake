//! Errors related to database operations

use std::path::PathBuf;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum DatabaseError {
    #[error("Invalid directory path: {0}")]
    InvalidRootDir(PathBuf),

    #[error("Invalid file path: {path:?} ({reason:?})")]
    InvalidFilePath { path: PathBuf, reason: String },

    #[error("Unexpected change number (expected {expected:?}, actual {actual:?})")]
    UnexpectedChangeNumber { expected: i64, actual: i64 },

    #[error("Not found: {kind:?} ({id:?})")]
    EntityNotFound { kind: String, id: String },
}
