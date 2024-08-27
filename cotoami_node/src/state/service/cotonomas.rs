use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use futures::future::FutureExt;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{CotonomaDetails, Pagination},
        NodeServiceExt, ServiceError,
    },
    state::NodeState,
};

const DEFAULT_SUB_PAGE_SIZE: i64 = 10;
const DEFAULT_RECENT_PAGE_SIZE: i64 = 100;

impl NodeState {
    pub async fn cotonoma(&self, id: Id<Cotonoma>) -> Result<Cotonoma, ServiceError> {
        self.get(move |ds| {
            let (cotonoma, _) = ds.try_get_cotonoma(&id)?;
            Ok(cotonoma)
        })
        .await
    }

    pub async fn cotonoma_details(
        &self,
        id: Id<Cotonoma>,
    ) -> Result<CotonomaDetails, ServiceError> {
        self.get(move |ds| {
            let (cotonoma, coto) = ds.try_get_cotonoma(&id)?;
            let supers = ds.super_cotonomas(&coto)?;
            let subs = ds.sub_cotonomas(&cotonoma.uuid, DEFAULT_SUB_PAGE_SIZE, 0)?;
            Ok(CotonomaDetails::new(cotonoma, coto, supers, subs))
        })
        .await
    }

    pub async fn cotonoma_by_name(
        &self,
        name: String,
        node: Id<Node>,
    ) -> Result<Cotonoma, ServiceError> {
        self.get(move |ds| {
            let (cotonoma, _) = ds.try_get_cotonoma_by_name(&name, &node)?;
            Ok(cotonoma)
        })
        .await
    }

    pub async fn sub_cotonomas(
        &self,
        id: Id<Cotonoma>,
        pagination: Pagination,
    ) -> Result<Paginated<Cotonoma>, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return ("sub_cotonomas", errors).into_result();
        }
        self.get(move |ds| {
            ds.sub_cotonomas(
                &id,
                pagination.page_size.unwrap_or(DEFAULT_SUB_PAGE_SIZE),
                pagination.page,
            )
        })
        .await
    }

    pub async fn recent_cotonomas(
        &self,
        node: Option<Id<Node>>,
        pagination: Pagination,
    ) -> Result<Paginated<Cotonoma>, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return ("recent_cotonomas", errors).into_result();
        }
        self.get(move |ds| {
            ds.recent_cotonomas(
                node.as_ref(),
                pagination.page_size.unwrap_or(DEFAULT_RECENT_PAGE_SIZE),
                pagination.page,
            )
        })
        .await
    }

    pub async fn post_cotonoma(
        self,
        input: CotonomaInput<'static>,
        post_to: Id<Cotonoma>,
        operator: Arc<Operator>,
    ) -> Result<(Cotonoma, Coto), ServiceError> {
        if let Err(errors) = input.validate() {
            return ("post_cotonoma", errors).into_result();
        }
        self.change_in_cotonoma(
            input,
            post_to,
            move |ds, input, cotonoma| ds.post_cotonoma(&input, cotonoma, operator.as_ref()),
            |parent, input, cotonoma| parent.post_cotonoma(input, cotonoma.uuid).boxed(),
        )
        .await
    }
}
