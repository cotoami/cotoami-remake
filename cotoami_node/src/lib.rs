use std::sync::Arc;

use parking_lot::Mutex;
use tokio::task::AbortHandle;

mod client;
mod event;
mod pubsub;
mod service;
mod state;
mod web;

pub use crate::web::launch_server;

pub mod prelude {
    pub use crate::{
        event::local::LocalNodeEvent,
        service::{error::*, models::*, *},
        state::{NodeConfig, NodeState},
        web::ServerConfig,
    };
}

#[derive(Clone)]
struct Abortables(Arc<Mutex<Vec<AbortHandle>>>);

impl Abortables {
    fn new() -> Self { Self(Arc::new(Mutex::new(Vec::new()))) }

    fn is_empty(&self) -> bool { self.0.lock().is_empty() }

    fn add(&self, abortable: AbortHandle) { self.0.lock().push(abortable); }

    fn abort_all(&self) {
        let mut abortables = self.0.lock();
        while let Some(abortable) = abortables.pop() {
            abortable.abort();
        }
    }
}
