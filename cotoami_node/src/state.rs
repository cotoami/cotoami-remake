use std::{fs, sync::Arc};

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use parking_lot::{MappedRwLockReadGuard, RwLock, RwLockReadGuard};
use tokio::task::spawn_blocking;
use validator::Validate;

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
        })
    }

    pub fn config(&self) -> &Arc<Config> { &self.config }

    pub fn db(&self) -> &Arc<Database> { &self.db }

    pub fn pubsub(&self) -> &Pubsub { &self.pubsub }

    pub fn server_conn(
        &self,
        server_id: &Id<Node>,
    ) -> Result<MappedRwLockReadGuard<ServerConnection>> {
        RwLockReadGuard::try_map(self.server_conns.read(), |conns| conns.get(server_id))
            .map_err(|_| anyhow!("ServerConnection for {} not found", server_id))
    }

    pub fn contains_server(&self, server_id: &Id<Node>) -> bool {
        self.server_conns.read().contains_key(server_id)
    }

    pub fn put_server_conn(&self, server_id: &Id<Node>, server_conn: ServerConnection) {
        self.server_conns.write().insert(*server_id, server_conn);
    }

    pub fn read_server_conns(&self) -> RwLockReadGuard<ServerConnections> {
        self.server_conns.read()
    }

    pub async fn restore_server_conns(&self) -> Result<()> {
        let db = self.db.clone();
        let (local_node, server_nodes) = spawn_blocking(move || {
            let mut db = db.new_session()?;
            let operator = db.local_node_as_operator()?;
            Ok::<_, anyhow::Error>((
                db.local_node_pair(&operator)?.1,
                db.all_server_nodes(&operator)?,
            ))
        })
        .await??;

        let mut server_conns = self.server_conns.write();
        server_conns.clear();
        for (server_node, _) in server_nodes.iter() {
            let server_conn = if server_node.disabled {
                ServerConnection::Disabled
            } else {
                ServerConnection::connect(server_node, local_node.clone(), self).await
            };
            server_conns.insert(server_node.node_id, server_conn);
        }
        Ok(())
    }
}
