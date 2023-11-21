use thiserror::Error;

#[derive(Error, Debug)]
pub enum NodeError {
    #[error("Wrong database role")]
    WrongDatabaseRole,
}
