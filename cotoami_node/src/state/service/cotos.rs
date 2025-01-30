use std::{slice, sync::Arc};

use anyhow::Result;
use cotoami_db::prelude::*;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
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
                cotonoma.as_ref(),
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
            let related_data = CotosRelatedData::fetch(ds, slice::from_ref(&coto))?;
            let outgoing_links = ds.outgoing_links(&[coto.uuid])?;
            Ok(CotoDetails::new(coto, related_data, outgoing_links))
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
}
