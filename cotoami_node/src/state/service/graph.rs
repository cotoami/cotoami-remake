use anyhow::Result;
use cotoami_db::prelude::*;

use crate::{
    service::{
        ServiceError,
        models::{CotoGraph, CotosRelatedData},
    },
    state::NodeState,
};

impl NodeState {
    pub async fn graph_from_coto(&self, coto_id: Id<Coto>) -> Result<CotoGraph, ServiceError> {
        self.get(move |ds| {
            let root_coto = ds.try_get_coto(&coto_id)?;
            let root_cotonoma = if root_coto.is_cotonoma {
                let (cotonoma, _) = ds.try_get_cotonoma_by_coto_id(&root_coto.uuid)?;
                Some(cotonoma)
            } else {
                None
            };
            graph(ds, root_coto, root_cotonoma)
        })
        .await
    }

    pub async fn graph_from_cotonoma(
        &self,
        cotonoma_id: Id<Cotonoma>,
    ) -> Result<CotoGraph, ServiceError> {
        self.get(move |ds| {
            let (root_cotonoma, root_coto) = ds.try_get_cotonoma_pair(&cotonoma_id)?;
            graph(ds, root_coto, Some(root_cotonoma))
        })
        .await
    }
}

fn graph(
    ds: &mut DatabaseSession<'_>,
    root_coto: Coto,
    root_cotonoma: Option<Cotonoma>,
) -> Result<CotoGraph> {
    let root_coto_id = root_coto.uuid;
    let graph = ds.graph(root_coto, true)?; // traverse until cotonomas
    let cotos: Vec<Coto> = graph.cotos.into_values().collect();
    let related_data = CotosRelatedData::fetch(ds, &cotos)?;
    let itos: Vec<Ito> = graph.itos.into_values().flatten().collect();
    Ok::<_, anyhow::Error>(CotoGraph::new(
        root_coto_id,
        root_cotonoma,
        cotos,
        related_data,
        itos,
    ))
}
