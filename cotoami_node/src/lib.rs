use core::future::Future;
use std::sync::Arc;

use parking_lot::Mutex;
use tokio::task::{AbortHandle, JoinHandle};

mod client;
mod config;
mod event;
mod pubsub;
mod service;
mod state;
mod web;

pub use crate::web::launch_server;

pub mod prelude {
    pub use crate::{
        config::NodeConfig,
        event::local::LocalNodeEvent,
        service::{command::*, error::*, models::*, service_ext::*, *},
        state::*,
        web::ServerConfig,
    };
}

#[derive(Clone, Default)]
pub struct Abortables(Arc<Mutex<Vec<AbortHandle>>>);

impl Abortables {
    pub fn is_empty(&self) -> bool { self.0.lock().is_empty() }

    pub fn add(&self, abortable: AbortHandle) { self.0.lock().push(abortable); }

    pub fn abort_all(&self) {
        let mut abortables = self.0.lock();
        while let Some(abortable) = abortables.pop() {
            abortable.abort();
        }
    }

    pub fn spawn<F>(&self, future: F) -> JoinHandle<F::Output>
    where
        F: Future + Send + 'static,
        F::Output: Send + 'static,
    {
        let join_handle = tokio::spawn(future);
        self.add(join_handle.abort_handle());
        join_handle
    }

    pub fn has_running_tasks(&self) -> bool {
        self.0
            .lock()
            .iter()
            .find(|task| !task.is_finished())
            .is_some()
    }
}
