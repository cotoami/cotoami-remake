use std::{collections::HashMap, sync::Arc};

use cotoami_db::prelude::*;
use parking_lot::RwLock;
use tokio::sync::oneshot::Sender;
use tracing::error;
use uuid::Uuid;

use crate::service::models::ActiveClient;

/////////////////////////////////////////////////////////////////////////////
// ClientConnection
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug)]
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

    pub fn client(&self) -> &ActiveClient { &self.client }

    pub fn disconnect(self) {
        let client_id = self.client_id();
        if let Err(e) = self.disconnect.send(()) {
            error!("Error disconnecting {client_id}: {e:?}");
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// ClientConnections
/////////////////////////////////////////////////////////////////////////////

#[derive(Clone, Default)]
pub struct ClientConnections(
    #[allow(clippy::type_complexity)] Arc<RwLock<HashMap<Id<Node>, ClientConnection>>>,
);

impl ClientConnections {
    pub(super) fn put(&self, client_conn: ClientConnection) {
        self.0.write().insert(client_conn.client_id(), client_conn);
    }

    pub(super) fn remove(&self, client_id: &Id<Node>) -> Option<ClientConnection> {
        self.0.write().remove(client_id)
    }

    pub fn disconnect(&self, client_id: &Id<Node>) {
        if let Some(conn) = self.remove(client_id) {
            conn.disconnect();
        }
    }

    pub fn disconnect_all(&self) {
        for (_, conn) in self.0.write().drain() {
            conn.disconnect();
        }
    }

    pub fn active_client(&self, client_id: &Id<Node>) -> Option<ActiveClient> {
        self.0.read().get(client_id).map(|conn| conn.client.clone())
    }

    pub fn active_clients(&self) -> Vec<ActiveClient> {
        self.0
            .read()
            .values()
            .map(|conn| conn.client.clone())
            .collect()
    }
}

/////////////////////////////////////////////////////////////////////////////
// AnonymousConnection
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug)]
pub struct AnonymousConnection {
    remote_addr: String,
    disconnect: Sender<()>,
}

impl AnonymousConnection {
    pub fn new(remote_addr: impl Into<String>, disconnect: Sender<()>) -> Self {
        Self {
            remote_addr: remote_addr.into(),
            disconnect,
        }
    }

    pub fn remote_addr(&self) -> &str { &self.remote_addr }

    pub fn disconnect(self) {
        let Self {
            remote_addr,
            disconnect,
        } = self;
        if let Err(e) = disconnect.send(()) {
            error!("Error disconnecting {remote_addr}: {e:?}");
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// AnonymousConnections
/////////////////////////////////////////////////////////////////////////////

#[derive(Clone, Default)]
pub struct AnonymousConnections(
    #[allow(clippy::type_complexity)] Arc<RwLock<HashMap<Uuid, AnonymousConnection>>>,
);

impl AnonymousConnections {
    pub fn count(&self) -> usize { self.0.read().len() }

    pub(super) fn add(&self, conn: AnonymousConnection) -> Uuid {
        let id = Uuid::now_v7();
        self.0.write().insert(id, conn);
        id
    }

    pub(super) fn remove(&self, id: &Uuid) -> Option<AnonymousConnection> {
        self.0.write().remove(id)
    }

    pub(crate) fn disconnect_all(&self) {
        for (_, conn) in self.0.write().drain() {
            conn.disconnect();
        }
    }
}
