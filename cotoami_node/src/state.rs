//! This module defines the global state ([NodeState]) and functions dealing with it.

use core::future::Future;
use std::{collections::HashMap, fs, io::ErrorKind, sync::Arc};

use anyhow::{anyhow, bail, Result};
use cotoami_db::prelude::*;
use parking_lot::RwLock;
use tokio::task::JoinHandle;
use tracing::debug;
use validator::Validate;

use crate::{service::NodeService, Abortables};

mod config;
mod conn;
mod internal;
mod pubsub;
mod service;

pub use self::{config::NodeConfig, conn::*, pubsub::*};

#[derive(Clone)]
pub struct NodeState {
    inner: Arc<State>,
}

struct State {
    config: Arc<NodeConfig>,
    db: Arc<Database>,
    pubsub: Pubsub,
    server_conns: ServerConnections,
    parent_services: ParentServices,
    abortables: Abortables,
}

impl NodeState {
    pub async fn new(config: NodeConfig) -> Result<Self> {
        config.validate()?;

        // Create the directory if it doesn't exist yet (a new database).
        let db_dir = config.db_dir();
        if let Err(e) = fs::create_dir(&db_dir) {
            match e.kind() {
                ErrorKind::AlreadyExists => (), // ignore
                _ => bail!("Unable to create a directory: {}", e.to_string()),
            }
        }

        // Open or create a database in the directory
        let db = Database::new(db_dir)?;

        let inner = State {
            config: Arc::new(config),
            db: Arc::new(db),
            pubsub: Pubsub::new(),
            server_conns: ServerConnections::new(),
            parent_services: ParentServices::new(),
            abortables: Abortables::new(),
        };
        let state = Self {
            inner: Arc::new(inner),
        };
        state.init().await?;
        Ok(state)
    }

    pub fn config(&self) -> &Arc<NodeConfig> { &self.inner.config }

    pub fn db(&self) -> &Arc<Database> { &self.inner.db }

    pub fn try_get_local_node_id(&self) -> Result<Id<Node>> {
        self.db().globals().try_get_local_node_id()
    }

    pub fn local_node_as_operator(&self) -> Result<Operator> {
        self.db().globals().local_node_as_operator()
    }

    pub fn pubsub(&self) -> &Pubsub { &self.inner.pubsub }

    pub fn server_conns(&self) -> &ServerConnections { &self.inner.server_conns }

    pub fn is_parent(&self, id: &Id<Node>) -> bool { self.db().globals().is_parent(id) }

    pub fn parent_services(&self) -> &ParentServices { &self.inner.parent_services }

    pub fn spawn_task<F>(&self, future: F) -> JoinHandle<F::Output>
    where
        F: Future + Send + 'static,
        F::Output: Send + 'static,
    {
        self.inner.abortables.spawn(future)
    }

    pub fn abort_tasks(&self) {
        self.server_conns().disconnect_all();
        self.inner.abortables.abort_all();
    }

    pub fn debug(&self, label: &str) {
        debug!(
            "NodeState inner pointers({label}): {}",
            Arc::strong_count(&self.inner)
        );
    }
}

impl Drop for State {
    fn drop(&mut self) {
        debug!(
            "NodeState [{:?}] is being destroyed.",
            self.config.node_name
        )
    }
}

#[derive(Clone)]
pub struct ParentServices(Arc<RwLock<HashMap<Id<Node>, Box<dyn NodeService>>>>);

impl ParentServices {
    fn new() -> Self { Self(Arc::new(RwLock::new(HashMap::default()))) }

    pub fn get(&self, parent_id: &Id<Node>) -> Option<Box<dyn NodeService>> {
        self.0
            .read()
            .get(parent_id)
            .map(|s| dyn_clone::clone_box(&**s))
    }

    pub fn try_get(&self, parent_id: &Id<Node>) -> Result<Box<dyn NodeService>> {
        self.get(parent_id)
            .ok_or(anyhow!("Parent disconnected: {parent_id}"))
    }

    pub fn put(&self, parent_id: Id<Node>, service: Box<dyn NodeService>) {
        self.0.write().insert(parent_id, service);
    }

    pub fn remove(&self, parent_id: &Id<Node>) -> Option<Box<dyn NodeService>> {
        debug!("Parent service being removed: {parent_id}");
        self.0.write().remove(parent_id)
    }
}

#[derive(Clone)]
pub struct ServerConnections(Arc<RwLock<HashMap<Id<Node>, ServerConnection>>>);

impl ServerConnections {
    fn new() -> Self { Self(Arc::new(RwLock::new(HashMap::default()))) }

    pub fn contains(&self, server_id: &Id<Node>) -> bool { self.0.read().contains_key(server_id) }

    pub fn get(&self, server_id: &Id<Node>) -> Option<ServerConnection> {
        self.0.read().get(server_id).map(Clone::clone)
    }

    pub fn try_get(&self, server_id: &Id<Node>) -> Result<ServerConnection> {
        self.get(server_id).ok_or(anyhow!(DatabaseError::not_found(
            EntityKind::ServerNode,
            "node_id",
            *server_id,
        )))
    }

    pub fn put(&self, server_id: Id<Node>, server_conn: ServerConnection) {
        self.0.write().insert(server_id, server_conn);
    }

    pub async fn connect_all(&self) {
        // NOTE: The each read lock guard is held across calls to .await.
        for conn in self.0.read().values() {
            conn.connect().await;
        }
    }

    pub fn disconnect_all(&self) {
        for conn in self.0.read().values() {
            conn.disconnect(None);
        }
    }
}
