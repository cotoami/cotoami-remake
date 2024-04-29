use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{
    service::{models::CotoGraph, ServiceError},
    state::NodeState,
};

impl NodeState {
    pub(crate) async fn graph_from_cotonoma(
        &self,
        cotonoma_id: Id<Cotonoma>,
    ) -> Result<CotoGraph, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;
            let (root_cotonoma, root_coto) = ds.try_get_cotonoma(&cotonoma_id)?;
            let graph = ds.graph(root_coto, true)?; // traverse until cotonomas
            let cotos: Vec<Coto> = graph.cotos.into_values().collect();
            let related_data = super::get_cotos_related_data(&mut ds, &cotos)?;
            let links: Vec<Link> = graph.links.into_values().flatten().collect();
            Ok::<_, anyhow::Error>(CotoGraph::new(
                root_cotonoma.coto_id,
                Some(root_cotonoma),
                cotos,
                related_data,
                links,
            ))
        })
        .await?
        .map_err(ServiceError::from)
    }
}
