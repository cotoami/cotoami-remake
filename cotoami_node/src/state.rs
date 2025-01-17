//! This module defines the global state ([NodeState]) and functions dealing with it.

use core::future::Future;
use std::{collections::HashMap, fs, io::ErrorKind, sync::Arc};

use anyhow::{anyhow, bail, Result};
use cotoami_db::prelude::*;
use parking_lot::RwLock;
use tokio::{sync::oneshot::Sender, task::JoinHandle};
use tracing::debug;
use validator::Validate;

use crate::{
    event::local::LocalNodeEvent,
    service::{models::ActiveClient, NodeService},
    Abortables,
};

mod config;
mod internal;
mod pubsub;
mod server_conn;
mod service;

pub use self::{config::NodeConfig, pubsub::*, server_conn::*};

#[derive(Clone)]
pub struct NodeState {
    inner: Arc<State>,
}

struct State {
    config: Arc<NodeConfig>,
    db: Arc<Database>,
    pubsub: Pubsub,
    server_conns: ServerConnections,
    client_conns: ClientConnections,
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
            pubsub: Pubsub::default(),
            server_conns: ServerConnections::default(),
            client_conns: ClientConnections::default(),
            parent_services: ParentServices::default(),
            abortables: Abortables::default(),
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

    pub fn client_conns(&self) -> &ClientConnections { &self.inner.client_conns }

    pub fn put_client_conn(&self, client_conn: ClientConnection) {
        self.pubsub()
            .publish_event(LocalNodeEvent::ClientConnected(client_conn.client.clone()));
        self.client_conns().put(client_conn);
    }

    pub fn remove_client_conn(&self, client_id: &Id<Node>, disconnection_error: Option<String>) {
        self.client_conns().remove(&client_id);
        self.pubsub()
            .publish_event(LocalNodeEvent::ClientDisconnected {
                node_id: *client_id,
                error: disconnection_error,
            });
    }

    pub fn active_clients(&self) -> Vec<ActiveClient> { self.client_conns().active_clients() }

    pub fn is_parent(&self, id: &Id<Node>) -> bool { self.db().globals().is_parent(id) }

    pub fn parent_services(&self) -> &ParentServices { &self.inner.parent_services }

    pub fn local_as_child(&self, parent_id: &Id<Node>) -> Option<ChildNode> {
        self.server_conns()
            .get(parent_id)
            .and_then(|conn| conn.local_as_child())
    }

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

#[derive(Clone, Default)]
pub struct ParentServices(
    #[allow(clippy::type_complexity)] Arc<RwLock<HashMap<Id<Node>, Box<dyn NodeService>>>>,
);

impl ParentServices {
    pub fn count(&self) -> usize { self.0.read().len() }

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

#[derive(Clone, Default)]
pub struct ServerConnections(
    #[allow(clippy::type_complexity)] Arc<RwLock<HashMap<Id<Node>, ServerConnection>>>,
);

impl ServerConnections {
    pub fn contains(&self, server_id: &Id<Node>) -> bool { self.0.read().contains_key(server_id) }

    pub fn get(&self, server_id: &Id<Node>) -> Option<ServerConnection> {
        self.0.read().get(server_id).cloned()
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

    #[allow(clippy::await_holding_lock)]
    pub async fn connect_all(&self) {
        // Configured the `send_guard` feature of parking_lot to be enabled,
        // so that the each read lock guard can be held across calls to .await.
        // https://github.com/Amanieu/parking_lot/issues/197
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

pub struct ClientConnection {
    client: ActiveClient,
    disconnect: Sender<()>,
}

impl ClientConnection {
    pub fn new(node_id: Id<Node>, remote_addr: String, disconnect: Sender<()>) -> Self {
        ClientConnection {
            client: ActiveClient::new(node_id, remote_addr),
            disconnect,
        }
    }

    pub fn client_id(&self) -> Id<Node> { self.client.node_id }

    pub fn disconnect(self) { let _ = self.disconnect.send(()); }
}

#[derive(Clone, Default)]
pub struct ClientConnections(
    #[allow(clippy::type_complexity)] Arc<RwLock<HashMap<Id<Node>, ClientConnection>>>,
);

impl ClientConnections {
    pub fn put(&self, client_conn: ClientConnection) {
        self.0.write().insert(client_conn.client_id(), client_conn);
    }

    pub fn remove(&self, client_id: &Id<Node>) -> Option<ClientConnection> {
        self.0.write().remove(client_id)
    }

    pub fn disconnect(&self, client_id: &Id<Node>) {
        if let Some(conn) = self.remove(client_id) {
            conn.disconnect();
        }
    }

    pub fn active_clients(&self) -> Vec<ActiveClient> {
        self.0
            .read()
            .values()
            .map(|conn| conn.client.clone())
            .collect()
    }
}
