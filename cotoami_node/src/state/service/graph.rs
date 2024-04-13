use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{
    service::{models::CotoGraph, ServiceError},
    state::NodeState,
};

impl NodeState {
    pub(crate) async fn coto_graph(&self, from: Id<Coto>) -> Result<CotoGraph, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;
            let root = ds.try_get_coto(&from)?;
            let graph = ds.graph(root, true)?; // traverse until cotonomas
            let cotos: Vec<Coto> = graph.cotos.into_values().collect();
            let related_data = super::get_cotos_related_data(&mut ds, &cotos)?;
            let links: Vec<Link> = graph.links.into_values().flatten().collect();
            Ok::<_, anyhow::Error>(CotoGraph::new(graph.root_id, cotos, related_data, links))
        })
        .await?
        .map_err(ServiceError::from)
    }
}
