use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{
    service::{
        models::{CotoGraph, CotosRelatedData},
        ServiceError,
    },
    state::NodeState,
};

impl NodeState {
    pub(crate) async fn graph_from_coto(
        &self,
        coto_id: Id<Coto>,
    ) -> Result<CotoGraph, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;
            let root_coto = ds.try_get_coto(&coto_id)?;
            let root_cotonoma = if root_coto.is_cotonoma {
                let (cotonoma, _) = ds.try_get_cotonoma_by_coto_id(&root_coto.uuid)?;
                Some(cotonoma)
            } else {
                None
            };
            graph(&mut ds, root_coto, root_cotonoma)
        })
        .await?
        .map_err(ServiceError::from)
    }

    pub(crate) async fn graph_from_cotonoma(
        &self,
        cotonoma_id: Id<Cotonoma>,
    ) -> Result<CotoGraph, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;
            let (root_cotonoma, root_coto) = ds.try_get_cotonoma(&cotonoma_id)?;
            graph(&mut ds, root_coto, Some(root_cotonoma))
        })
        .await?
        .map_err(ServiceError::from)
    }
}

fn graph<'a>(
    ds: &mut DatabaseSession<'a>,
    root_coto: Coto,
    root_cotonoma: Option<Cotonoma>,
) -> Result<CotoGraph> {
    let root_coto_id = root_coto.uuid;
    let graph = ds.graph(root_coto, true)?; // traverse until cotonomas
    let cotos: Vec<Coto> = graph.cotos.into_values().collect();
    let related_data = CotosRelatedData::fetch(ds, &cotos)?;
    let links: Vec<Link> = graph.links.into_values().flatten().collect();
    Ok::<_, anyhow::Error>(CotoGraph::new(
        root_coto_id,
        root_cotonoma,
        cotos,
        related_data,
        links,
    ))
}
