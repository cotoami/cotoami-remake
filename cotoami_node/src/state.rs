//! This module defines the global state ([NodeState]) and functions dealing with it.

use std::{collections::HashMap, fs, sync::Arc};

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use parking_lot::{MappedRwLockWriteGuard, RwLock, RwLockReadGuard, RwLockWriteGuard};
use tracing::debug;
use validator::Validate;

use crate::service::NodeService;

mod config;
mod conn;
mod error;
mod impls;
mod pubsub;
mod service;

pub use self::{config::Config, conn::*, pubsub::*};

#[derive(Clone)]
pub struct NodeState {
    inner: Arc<State>,
}

struct State {
    config: Arc<Config>,
    db: Arc<Database>,
    pubsub: Pubsub,
    server_conns: Arc<RwLock<ServerConnections>>,
    parent_services: Arc<RwLock<ParentNodeServices>>,
}

impl NodeState {
    pub fn new(config: Config) -> Result<Self> {
        config.validate()?;

        let db_dir = config.db_dir();
        fs::create_dir(&db_dir).ok();
        let db = Database::new(db_dir)?;

        let pubsub = Pubsub::new();

        let inner = State {
            config: Arc::new(config),
            db: Arc::new(db),
            pubsub,
            server_conns: Arc::new(RwLock::new(ServerConnections::default())),
            parent_services: Arc::new(RwLock::new(ParentNodeServices::default())),
        };

        Ok(NodeState {
            inner: Arc::new(inner),
        })
    }

    pub fn config(&self) -> &Arc<Config> { &self.inner.config }

    pub fn db(&self) -> &Arc<Database> { &self.inner.db }

    pub fn pubsub(&self) -> &Pubsub { &self.inner.pubsub }

    pub fn read_server_conns(&self) -> RwLockReadGuard<ServerConnections> {
        self.inner.server_conns.read()
    }

    pub fn contains_server(&self, server_id: &Id<Node>) -> bool {
        self.read_server_conns().contains_key(server_id)
    }

    pub fn write_server_conns(&self) -> RwLockWriteGuard<ServerConnections> {
        self.inner.server_conns.write()
    }

    pub fn put_server_conn(&self, server_id: &Id<Node>, server_conn: ServerConnection) {
        self.write_server_conns().insert(*server_id, server_conn);
    }

    pub fn write_server_conn(
        &self,
        server_id: &Id<Node>,
    ) -> Result<MappedRwLockWriteGuard<ServerConnection>> {
        RwLockWriteGuard::try_map(self.write_server_conns(), |conns| conns.get_mut(server_id))
            .map_err(|_| anyhow!("ServerConnection for [{server_id}] not found"))
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

    pub fn put_parent_service(&self, parent_id: Id<Node>, service: Box<dyn NodeService>) {
        debug!("Parent service being registered: {parent_id}");
        self.inner
            .parent_services
            .write()
            .insert(parent_id, service);
    }

    pub fn remove_parent_service(&self, parent_id: &Id<Node>) -> Option<Box<dyn NodeService>> {
        debug!("Parent service being removed: {parent_id}");
        self.inner.parent_services.write().remove(parent_id)
    }
}

type ParentNodeServices = HashMap<Id<Node>, Box<dyn NodeService>>;
