use std::sync::Arc;

use anyhow::{anyhow, Result};
use cotoami_db::prelude::*;
use futures::future::FutureExt;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{CotosPage, GeolocatedCotos, Pagination},
        NodeServiceExt, ServiceError,
    },
    state::NodeState,
};

const DEFAULT_PAGE_SIZE: i64 = 20;
const GEOLOCATED_COTOS_MAX_SIZE: i64 = 30;

impl NodeState {
    pub async fn coto(&self, id: Id<Coto>) -> Result<Coto, ServiceError> {
        self.get(move |ds| ds.try_get_coto(&id)).await
    }

    pub async fn recent_cotos(
        &self,
        node: Option<Id<Node>>,
        cotonoma: Option<Id<Cotonoma>>,
        pagination: Pagination,
    ) -> Result<CotosPage, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return errors.into_result();
        }
        self.get(move |ds| {
            let page = ds.recent_cotos(
                node.as_ref(),
                cotonoma.as_ref(),
                pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
                pagination.page,
            )?;
            CotosPage::new(page, ds)
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
        pagination: Pagination,
    ) -> Result<CotosPage, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return errors.into_result();
        }
        self.get(move |ds| {
            let page = ds.search_cotos(
                &query,
                node.as_ref(),
                cotonoma.as_ref(),
                pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
                pagination.page,
            )?;
            CotosPage::new(page, ds)
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
        self.change_in_cotonoma(
            input,
            post_to,
            move |ds, input, cotonoma| ds.post_coto(&input, cotonoma, operator.as_ref()),
            |parent, input, cotonoma| parent.post_coto(input, cotonoma.uuid).boxed(),
        )
        .await
    }

    pub async fn delete_coto(
        self,
        id: Id<Coto>,
        operator: Arc<Operator>,
    ) -> Result<Id<Coto>, ServiceError> {
        let coto = self.coto(id).await?;
        let cotonoma_id = coto
            .posted_in_id
            .ok_or(anyhow!("A root cotonoma can't be deleted."))?;
        self.change_in_cotonoma(
            id,
            cotonoma_id,
            move |ds, id, _| {
                let changelog = ds.delete_coto(&id, operator.as_ref())?;
                Ok((id, changelog))
            },
            |parent, id, _| parent.delete_coto(id).boxed(),
        )
        .await
    }
}
