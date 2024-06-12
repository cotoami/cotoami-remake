//! This module defines the global state ([NodeState]) and functions dealing with it.

use std::{collections::HashMap, fs, io::ErrorKind, sync::Arc};

use anyhow::{anyhow, bail, Result};
use cotoami_db::prelude::*;
use parking_lot::{RwLock, RwLockReadGuard, RwLockWriteGuard};
use tracing::debug;
use validator::Validate;

use crate::service::NodeService;

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
    server_conns: Arc<RwLock<ServerConnections>>,
    parent_services: Arc<RwLock<ParentNodeServices>>,
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
            server_conns: Arc::new(RwLock::new(ServerConnections::default())),
            parent_services: Arc::new(RwLock::new(ParentNodeServices::default())),
        };
        let state = Self {
            inner: Arc::new(inner),
        };
        state.init().await?;
        Ok(state)
    }

    pub fn config(&self) -> &Arc<NodeConfig> { &self.inner.config }

    pub fn db(&self) -> &Arc<Database> { &self.inner.db }

    pub fn local_node_id(&self) -> Result<Id<Node>> { self.db().globals().local_node_id() }

    pub fn local_node_as_operator(&self) -> Result<Operator> {
        self.db().globals().local_node_as_operator()
    }

    pub fn pubsub(&self) -> &Pubsub { &self.inner.pubsub }

    pub fn read_server_conns(&self) -> RwLockReadGuard<ServerConnections> {
        self.inner.server_conns.read()
    }

    pub fn contains_server(&self, server_id: &Id<Node>) -> bool {
        self.read_server_conns().contains_key(server_id)
    }

    pub fn server_conn(&self, server_id: &Id<Node>) -> Result<ServerConnection> {
        self.read_server_conns()
            .get(server_id)
            .ok_or(anyhow!(DatabaseError::not_found(
                EntityKind::ServerNode,
                "node_id",
                *server_id,
            )))
            .map(Clone::clone)
    }

    pub fn write_server_conns(&self) -> RwLockWriteGuard<ServerConnections> {
        self.inner.server_conns.write()
    }

    pub fn put_server_conn(&self, server_id: &Id<Node>, server_conn: ServerConnection) {
        self.write_server_conns().insert(*server_id, server_conn);
    }

    pub fn is_parent(&self, id: &Id<Node>) -> bool { self.db().globals().is_parent(id) }

    pub fn read_parent_services(&self) -> RwLockReadGuard<ParentNodeServices> {
        self.inner.parent_services.read()
    }

    pub fn parent_service(&self, parent_id: &Id<Node>) -> Option<Box<dyn NodeService>> {
        self.read_parent_services()
            .get(parent_id)
            .map(|s| dyn_clone::clone_box(&**s))
    }

    pub fn parent_service_or_err(&self, parent_id: &Id<Node>) -> Result<Box<dyn NodeService>> {
        self.parent_service(parent_id)
            .ok_or(anyhow!("Parent disconnected: {parent_id}"))
    }

    pub fn remove_parent_service(&self, parent_id: &Id<Node>) -> Option<Box<dyn NodeService>> {
        debug!("Parent service being removed: {parent_id}");
        self.inner.parent_services.write().remove(parent_id)
    }
}

type ParentNodeServices = HashMap<Id<Node>, Box<dyn NodeService>>;
