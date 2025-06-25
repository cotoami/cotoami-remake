//! This module defines the global state ([NodeState]) and functions dealing with it.

use core::future::Future;
use std::{collections::HashMap, path::Path, sync::Arc};

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use parking_lot::{RwLock, RwLockReadGuard};
use semver::{Version, VersionReq};
use tokio::{
    sync::oneshot::Sender,
    task::{spawn_blocking, JoinHandle},
};
use tracing::debug;
use uuid::Uuid;
use validator::Validate;

use crate::{
    config::{NodeConfig, ServerConfig},
    event::local::LocalNodeEvent,
    service::{
        error::{IntoServiceResult, RequestError, ServiceError},
        models::ActiveClient,
        NodeService,
    },
    Abortables,
};

mod client_conn;
mod error;
mod internal;
mod plugins;
mod pubsub;
mod server_conn;
mod service;

pub use self::{client_conn::*, error::*, plugins::*, pubsub::*, server_conn::*};

#[derive(Clone)]
pub struct NodeState {
    inner: Arc<State>,
}

struct State {
    version: String,
    client_version_requirement: VersionReq,
    config: Arc<RwLock<NodeConfig>>,
    db: Arc<Database>,
    pubsub: Pubsub,
    server_conns: ServerConnections,
    client_conns: ClientConnections,
    anonymous_conns: AnonymousConnections,
    parent_services: ParentServices,
    abortables: Abortables,
    local_server_config: RwLock<Option<Arc<ServerConfig>>>,
    plugins: RwLock<Plugins>,
}

impl NodeState {
    pub const CLIENT_VERSION_REQUIREMENT: &str = ">=0.7.0";

    pub async fn new(config: NodeConfig) -> Result<Self> {
        config.validate()?;

        // Open or create a database in the directory
        let db_dir = config.db_dir();
        crate::create_dir_if_not_exist(&db_dir)?;
        let db = Database::new(db_dir)?;

        let inner = State {
            version: env!("CARGO_PKG_VERSION").into(),
            client_version_requirement: VersionReq::parse(Self::CLIENT_VERSION_REQUIREMENT)?,
            config: Arc::new(RwLock::new(config)),
            db: Arc::new(db),
            pubsub: Pubsub::default(),
            server_conns: ServerConnections::default(),
            client_conns: ClientConnections::default(),
            anonymous_conns: AnonymousConnections::default(),
            parent_services: ParentServices::default(),
            abortables: Abortables::default(),
            local_server_config: RwLock::new(None),
            plugins: RwLock::new(Plugins::default()),
        };
        let state = Self {
            inner: Arc::new(inner),
        };
        state.init().await?;
        Ok(state)
    }

    pub fn version(&self) -> &str { &self.inner.version }

    pub(crate) fn check_client_version(&self, client_version: &str) -> Result<(), ServiceError> {
        let client_version = Version::parse(client_version)?;
        if self
            .inner
            .client_version_requirement
            .matches(&client_version)
        {
            Ok(())
        } else {
            RequestError::new(
                "invalid-client-version",
                format!(
                    "Client version {} does not match the required version: {}",
                    client_version,
                    Self::CLIENT_VERSION_REQUIREMENT
                ),
            )
            .into_result()
        }
    }

    pub fn config_arc(&self) -> Arc<RwLock<NodeConfig>> { self.inner.config.clone() }

    pub fn read_config(&self) -> RwLockReadGuard<NodeConfig> { self.inner.config.read() }

    pub fn db(&self) -> &Arc<Database> { &self.inner.db }

    pub fn try_get_local_node_id(&self) -> Result<Id<Node>> {
        self.db().globals().try_get_local_node_id()
    }

    pub fn local_node_as_operator(&self) -> Result<Operator> {
        self.db().globals().local_node_as_operator()
    }

    pub async fn generate_owner_password(
        &self,
        current_password: Option<String>,
    ) -> Result<String> {
        spawn_blocking({
            let state = self.clone();
            move || {
                let new_password = cotoami_db::generate_secret(None);

                // Change or newly set the password
                let ds = state.db().new_session()?;
                if let Some(ref current_password) = current_password {
                    ds.change_owner_password(&new_password, current_password)?;
                } else {
                    ds.set_owner_password_if_none(&new_password)?;
                }

                // Update the [NodeConfig::owner_password] in the node state
                state.inner.config.write().owner_password = Some(new_password.clone());
                Ok(new_password)
            }
        })
        .await?
    }

    pub fn pubsub(&self) -> &Pubsub { &self.inner.pubsub }

    pub fn server_conns(&self) -> &ServerConnections { &self.inner.server_conns }

    pub fn client_conns(&self) -> &ClientConnections { &self.inner.client_conns }

    pub(crate) fn put_client_conn(&self, client_conn: ClientConnection) {
        self.pubsub().publish_event(LocalNodeEvent::ClientConnected(
            client_conn.client().clone(),
        ));
        self.client_conns().put(client_conn);
    }

    pub(crate) fn on_client_disconnect(
        &self,
        client_id: Id<Node>,
        disconnection_error: Option<String>,
    ) {
        self.client_conns().remove(&client_id);
        self.client_disconnected(client_id, disconnection_error);
    }

    pub fn active_clients(&self) -> Vec<ActiveClient> { self.client_conns().active_clients() }

    pub fn anonymous_conns(&self) -> &AnonymousConnections { &self.inner.anonymous_conns }

    pub(crate) fn add_anonymous_conn(
        &self,
        remote_addr: impl Into<String>,
        disconnect: Sender<()>,
    ) -> Uuid {
        self.anonymous_conns()
            .add(AnonymousConnection::new(remote_addr, disconnect))
    }

    pub(crate) fn remove_anonymous_conn(&self, id: &Uuid) { self.anonymous_conns().remove(id); }

    pub fn is_parent(&self, id: &Id<Node>) -> bool { self.db().globals().is_parent(id) }

    pub fn parent_services(&self) -> &ParentServices { &self.inner.parent_services }

    pub fn child_privileges(&self, parent_id: &Id<Node>) -> Option<ChildNode> {
        self.server_conns()
            .get(parent_id)
            .and_then(|conn| conn.child_privileges())
    }

    pub fn spawn_task<F>(&self, future: F) -> JoinHandle<F::Output>
    where
        F: Future + Send + 'static,
        F::Output: Send + 'static,
    {
        self.inner.abortables.spawn(future)
    }

    pub async fn shutdown(&self) {
        debug!("NodeState is shutting down...");
        self.server_conns().disconnect_all().await;
        self.inner.abortables.abort_all();
    }

    pub fn set_local_server_config(&self, config: Arc<ServerConfig>) {
        self.inner.local_server_config.write().replace(config);
    }

    pub fn local_server_config(&self) -> Option<Arc<ServerConfig>> {
        self.inner.local_server_config.read().clone()
    }

    pub fn load_plugins_from_dir<P: AsRef<Path>>(&self, plugins_dir: P) -> Result<()> {
        self.inner.plugins.write().load_from_dir(plugins_dir)?;
        Ok(())
    }
}

impl Drop for State {
    fn drop(&mut self) {
        debug!(
            "NodeState [{:?}] has been destroyed.",
            self.config.read().node_name
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

    fn put(&self, parent_id: Id<Node>, service: Box<dyn NodeService>) {
        self.0.write().insert(parent_id, service);
    }

    fn remove(&self, parent_id: &Id<Node>) -> Option<Box<dyn NodeService>> {
        debug!("Parent service being removed: {parent_id}");
        self.0.write().remove(parent_id)
    }
}
