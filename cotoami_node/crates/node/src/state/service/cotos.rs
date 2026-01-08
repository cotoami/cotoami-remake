use std::{slice, sync::Arc};

use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    service::{
        error::{IntoServiceResult, RequestError},
        models::{CotoDetails, CotosRelatedData, GeolocatedCotos, PaginatedCotos, Pagination},
        NodeServiceExt, ServiceError,
    },
    state::NodeState,
};

const DEFAULT_PAGE_SIZE: i64 = 20;
const GEOLOCATED_COTOS_MAX_SIZE: i64 = 30;

impl NodeState {
    pub async fn recent_cotos(
        &self,
        node: Option<Id<Node>>,
        cotonoma: Option<Id<Cotonoma>>,
        only_cotonomas: bool,
        pagination: Pagination,
    ) -> Result<PaginatedCotos, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return errors.into_result();
        }
        self.get(move |ds| {
            let page = ds.recent_cotos(
                node.as_ref(),
                cotonoma.map(|c| (c, CotonomaScope::Local)),
                only_cotonomas,
                pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
                pagination.page,
            )?;
            PaginatedCotos::new(page, ds)
        })
        .await
    }

    pub async fn geolocated_cotos(
        &self,
        node: Option<Id<Node>>,
        cotonoma: Option<Id<Cotonoma>>,
    ) -> Result<GeolocatedCotos, ServiceError> {
        self.get(move |ds| {
            let cotos =
                ds.geolocated_cotos(node.as_ref(), cotonoma.as_ref(), GEOLOCATED_COTOS_MAX_SIZE)?;
            GeolocatedCotos::new(cotos, ds)
        })
        .await
    }

    pub async fn cotos_in_geo_bounds(
        &self,
        southwest: Geolocation,
        northeast: Geolocation,
    ) -> Result<GeolocatedCotos, ServiceError> {
        self.get(move |ds| {
            let cotos =
                ds.cotos_in_geo_bounds(&southwest, &northeast, GEOLOCATED_COTOS_MAX_SIZE)?;
            GeolocatedCotos::new(cotos, ds)
        })
        .await
    }

    pub async fn search_cotos(
        &self,
        query: String,
        node: Option<Id<Node>>,
        cotonoma: Option<Id<Cotonoma>>,
        only_cotonomas: bool,
        pagination: Pagination,
    ) -> Result<PaginatedCotos, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return errors.into_result();
        }
        self.get(move |ds| {
            let page = ds.search_cotos(
                &query,
                node.as_ref(),
                cotonoma.as_ref(),
                only_cotonomas,
                pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
                pagination.page,
            )?;
            PaginatedCotos::new(page, ds)
        })
        .await
    }

    pub async fn coto(&self, id: Id<Coto>) -> Result<Coto, ServiceError> {
        self.get(move |ds| ds.try_get_coto(&id)).await
    }

    pub async fn coto_details(&self, id: Id<Coto>) -> Result<CotoDetails, ServiceError> {
        self.get(move |ds| {
            let coto = ds.try_get_coto(&id)?;
            let outgoing_itos = ds.outgoing_itos(&[id])?;
            let (incoming_itos, incoming_neighbors) = ds.incoming_neighbors(&id)?;
            let cotos = [slice::from_ref(&coto), incoming_neighbors.as_ref()].concat();
            let related_data = CotosRelatedData::fetch(ds, &cotos)?;
            Ok(CotoDetails::new(
                coto,
                [outgoing_itos, incoming_itos].concat(),
                incoming_neighbors,
                related_data,
            ))
        })
        .await
    }

    pub async fn post_coto(
        self,
        input: CotoInput<'static>,
        post_to: Id<Cotonoma>,
        operator: Arc<Operator>,
    ) -> Result<Coto, ServiceError> {
        if let Err(errors) = input.validate() {
            return errors.into_result();
        }
        let cotonoma = self.cotonoma(post_to).await?;
        self.change(
            cotonoma.node_id,
            input,
            move |ds, input| ds.post_coto(&input, &post_to, operator.as_ref()),
            |parent, input| parent.post_coto(input, post_to),
        )
        .await
    }

    pub async fn edit_coto(
        self,
        id: Id<Coto>,
        diff: CotoContentDiff<'static>,
        operator: Arc<Operator>,
    ) -> Result<Coto, ServiceError> {
        if let Err(errors) = diff.validate() {
            return errors.into_result();
        }
        let coto = self.coto(id).await?;
        self.change(
            coto.node_id,
            diff,
            move |ds, diff| ds.edit_coto(&id, diff, operator.as_ref()),
            |parent, diff| parent.edit_coto(id, diff),
        )
        .await
    }

    pub async fn promote(
        self,
        id: Id<Coto>,
        operator: Arc<Operator>,
    ) -> Result<(Cotonoma, Coto), ServiceError> {
        let coto = self.coto(id).await?;
        self.change(
            coto.node_id,
            id,
            move |ds, id| ds.promote(&id, operator.as_ref()),
            |parent, id| parent.promote(id),
        )
        .await
    }

    pub async fn delete_coto(
        self,
        id: Id<Coto>,
        operator: Arc<Operator>,
    ) -> Result<Id<Coto>, ServiceError> {
        let coto = self.coto(id).await?;
        self.change(
            coto.node_id,
            id,
            move |ds, coto_id| {
                let changelog = ds.delete_coto(&coto_id, operator.as_ref())?;
                Ok((coto_id, changelog))
            },
            |parent, coto_id| parent.delete_coto(coto_id),
        )
        .await
    }

    pub async fn repost(
        self,
        id: Id<Coto>,
        dest: Id<Cotonoma>,
        operator: Arc<Operator>,
    ) -> Result<(Coto, Coto), ServiceError> {
        let cotonoma = self.cotonoma(dest).await?;
        self.change(
            cotonoma.node_id,
            (id, cotonoma),
            move |ds, (id, cotonoma)| ds.repost(&id, &cotonoma, operator.as_ref()),
            |parent, (id, cotonoma)| parent.repost(id, cotonoma.uuid),
        )
        .await
    }

    pub async fn post_subcoto(
        self,
        source_coto_id: Id<Coto>,
        input: CotoInput<'static>,
        post_to: Option<Id<Cotonoma>>,
        operator: Arc<Operator>,
    ) -> Result<(Coto, Ito), ServiceError> {
        if let Err(errors) = input.validate() {
            return errors.into_result();
        }

        let local_node_id = self.try_get_local_node_id()?;
        let source_coto = self.coto(source_coto_id).await?;
        let post_to = self
            .determine_subcoto_destination(&source_coto, post_to)
            .await?;
        let ito_node_id =
            Ito::determine_node(&source_coto.node_id, &post_to.node_id, &local_node_id);

        // Ensure the coto and ito to be created in the same node.
        if ito_node_id != post_to.node_id {
            return RequestError::new(
                "invalid-subcoto-destination",
                "Invalid subcoto destination.",
            )
            .into_result();
        }

        if post_to.node_id == local_node_id {
            // Post to the local node.
            spawn_blocking({
                let this = self.clone();
                move || {
                    let (subcoto, logs) = this.db().new_session()?.post_subcoto(
                        &source_coto_id,
                        &input,
                        &post_to.uuid,
                        &operator,
                    )?;
                    for log in logs {
                        this.pubsub().publish_change(log);
                    }
                    Ok(subcoto)
                }
            })
            .await?
        } else {
            // Send the change to a remote node.
            if let Some(parent_service) = self.parent_services().get(&post_to.node_id) {
                parent_service
                    .post_subcoto(source_coto_id, input, Some(post_to.uuid))
                    .await
                    .map_err(ServiceError::from)
            } else {
                Err(ServiceError::Permission)
            }
        }
    }

    async fn determine_subcoto_destination(
        &self,
        source_coto: &Coto,
        post_to: Option<Id<Cotonoma>>,
    ) -> Result<Cotonoma, ServiceError> {
        if let Some(post_to) = post_to {
            self.cotonoma(post_to).await
        } else {
            if source_coto.is_cotonoma {
                let (cotonoma, _) = self.cotonoma_pair_by_coto_id(source_coto.uuid).await?;
                Ok(cotonoma)
            } else {
                // non-cotonoma coto should have posted_in_id
                self.cotonoma(source_coto.posted_in_id.unwrap()).await
            }
        }
    }
}
