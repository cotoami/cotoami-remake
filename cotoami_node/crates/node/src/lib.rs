use core::future::Future;
use std::{fs, io::ErrorKind, path::Path, sync::Arc};

use anyhow::{bail, Result};
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
        config::*,
        event::local::LocalNodeEvent,
        service::{command::*, error::*, models::*, service_ext::*, *},
        state::*,
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

    pub fn has_running_tasks(&self) -> bool { self.0.lock().iter().any(|task| !task.is_finished()) }
}

pub fn create_dir_if_not_exist<P: AsRef<Path>>(path: P) -> Result<()> {
    if let Err(e) = fs::create_dir(path) {
        match e.kind() {
            ErrorKind::AlreadyExists => (), // ignore
            _ => bail!("Unable to create a directory: {e}"),
        }
    }
    Ok(())
}
