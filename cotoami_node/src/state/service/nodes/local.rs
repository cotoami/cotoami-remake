use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{
    service::{models::LocalServer, ServiceError},
    state::NodeState,
};

impl NodeState {
    pub async fn local_node(&self) -> Result<Node, ServiceError> {
        self.get(move |ds| ds.local_node()).await
    }

    pub async fn local_node_root(&self) -> Result<Option<(Cotonoma, Coto)>, ServiceError> {
        self.get(move |ds| ds.local_node_root()).await
    }

    pub fn local_server(&self, operator: Arc<Operator>) -> Result<LocalServer, ServiceError> {
        operator.requires_to_be_owner()?;
        let local = self.db().globals().try_read_local_node()?;
        Ok(LocalServer {
            active_config: self.local_server_config().map(|arc| arc.as_ref().clone()),
            anonymous_read_enabled: local.anonymous_read_enabled,
            anonymous_connections: self.anonymous_conns().count(),
        })
    }

    pub async fn set_local_node_icon(
        self,
        icon: bytes::Bytes,
        operator: Arc<Operator>,
    ) -> Result<Node, ServiceError> {
        self.change_local(move |ds| ds.set_local_node_icon(icon.as_ref(), operator.as_ref()))
            .await
    }

    pub async fn enable_anonymous_read(
        self,
        enable: bool,
        operator: Arc<Operator>,
    ) -> Result<bool, ServiceError> {
        if !enable {
            self.anonymous_conns().disconnect_all();
        }

        let db = self.db().clone();
        spawn_blocking(move || {
            db.new_session()?.enable_anonymous_read(enable, &operator)?;
            Ok(enable)
        })
        .await?
    }
}
