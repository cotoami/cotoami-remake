use thiserror::Error;

#[derive(Error, Debug)]
pub enum NodeError {
    #[error("Owner authentication failed")]
    OwnerAuthenticationFailed,
}
