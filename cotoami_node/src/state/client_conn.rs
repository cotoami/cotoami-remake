use std::{collections::HashMap, sync::Arc};

use cotoami_db::prelude::*;
use parking_lot::RwLock;
use tokio::sync::oneshot::Sender;
use tracing::error;

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
