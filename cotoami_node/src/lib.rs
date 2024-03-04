mod client;
mod event;
mod pubsub;
mod service;
mod state;
mod web;

pub use crate::web::launch_server;

pub mod prelude {
    pub use crate::{
        state::{NodeConfig, NodeState},
        web::ServerConfig,
    };
}
