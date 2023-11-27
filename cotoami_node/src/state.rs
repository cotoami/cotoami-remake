//! This module defines the global state ([NodeState]) and functions dealing with it.

use std::{collections::HashMap, fs, sync::Arc};

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use parking_lot::{MappedRwLockReadGuard, RwLock, RwLockReadGuard};
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

        Ok(NodeState {
            config: Arc::new(config),
            db: Arc::new(db),
            pubsub,
            server_conns: Arc::new(RwLock::new(ServerConnections::default())),
            parent_services: Arc::new(RwLock::new(ParentNodeServices::default())),
        })
    }

    pub fn config(&self) -> &Arc<Config> { &self.config }

    pub fn db(&self) -> &Arc<Database> { &self.db }

    pub fn pubsub(&self) -> &Pubsub { &self.pubsub }

    pub fn contains_server(&self, server_id: &Id<Node>) -> bool {
        self.server_conns.read().contains_key(server_id)
    }

    pub fn read_server_conns(&self) -> RwLockReadGuard<ServerConnections> {
        self.server_conns.read()
    }

    pub fn server_conn(
        &self,
        server_id: &Id<Node>,
    ) -> Result<MappedRwLockReadGuard<ServerConnection>> {
        RwLockReadGuard::try_map(self.read_server_conns(), |conns| conns.get(server_id))
            .map_err(|_| anyhow!("ServerConnection for [{}] not found", server_id))
    }

    pub fn put_server_conn(&self, server_id: &Id<Node>, server_conn: ServerConnection) {
        self.server_conns.write().insert(*server_id, server_conn);
    }

    pub fn is_parent(&self, id: &Id<Node>) -> bool { self.db().globals().is_parent(id) }

    pub fn read_parent_services(&self) -> RwLockReadGuard<ParentNodeServices> {
        self.parent_services.read()
    }

    pub fn parent_service(&self, parent_id: &Id<Node>) -> Option<Box<dyn NodeService>> {
        self.read_parent_services()
            .get(parent_id)
            .map(|s| dyn_clone::clone_box(&**s))
    }

    pub fn parent_service_or_err(&self, parent_id: &Id<Node>) -> Result<Box<dyn NodeService>> {
        self.parent_service(parent_id)
            .ok_or(anyhow!("Parent disconnected: {}", parent_id))
    }

    pub fn put_parent_service(&self, parent_id: Id<Node>, service: Box<dyn NodeService>) {
        self.parent_services.write().insert(parent_id, service);
    }

    pub fn remove_parent_service(&self, parent_id: &Id<Node>) -> Option<Box<dyn NodeService>> {
        self.parent_services.write().remove(parent_id)
    }
}

type ParentNodeServices = HashMap<Id<Node>, Box<dyn NodeService>>;
