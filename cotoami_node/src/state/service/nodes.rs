use std::{collections::HashMap, sync::Arc};

use anyhow::Result;
use chrono::NaiveDateTime;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{
    service::{models::NodeDetails, ServiceError},
    state::NodeState,
};

mod children;
mod clients;
mod local;
mod parents;
mod servers;

impl NodeState {
    pub async fn all_nodes(&self) -> Result<Vec<Node>, ServiceError> {
        self.get(move |ds| ds.all_nodes()).await
    }

    pub async fn node_details(&self, id: Id<Node>) -> Result<NodeDetails, ServiceError> {
        self.get(move |ds| {
            let node = ds.try_get_node(&id)?;
            let root = if let Some(cotonoma_id) = node.root_cotonoma_id {
                ds.cotonoma_pair(&cotonoma_id)?
            } else {
                None
            };
            Ok(NodeDetails::new(node, root))
        })
        .await
    }

    pub async fn disconnect_from(&self, node_id: &Id<Node>) {
        self.client_conns().disconnect(node_id);
        self.server_conns().disconnect(node_id).await;
    }

    pub async fn others_last_posted_at(
        &self,
        operator: Arc<Operator>,
    ) -> Result<HashMap<Id<Node>, NaiveDateTime>, ServiceError> {
        self.get(move |ds| ds.others_last_posted_at(&operator))
            .await
    }

    pub async fn mark_all_as_read(
        &self,
        operator: Arc<Operator>,
    ) -> Result<NaiveDateTime, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || {
            db.new_session()?
                .mark_all_as_read(None, &operator)
                .map_err(ServiceError::from)
        })
        .await?
    }

    pub async fn mark_as_read(
        &self,
        node_id: Id<Node>,
        operator: Arc<Operator>,
    ) -> Result<NaiveDateTime, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || {
            db.new_session()?
                .mark_as_read(&node_id, None, &operator)
                .map_err(ServiceError::from)
        })
        .await?
    }
}
