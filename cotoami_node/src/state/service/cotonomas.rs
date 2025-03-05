use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
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
const DEFAULT_COTONOMAS_BY_PREFIX_LIMIT: i64 = 10;

impl NodeState {
    pub async fn recent_cotonomas(
        &self,
        node: Option<Id<Node>>,
        pagination: Pagination,
    ) -> Result<Page<Cotonoma>, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return errors.into_result();
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

    pub async fn all_node_roots(&self) -> Result<Vec<(Cotonoma, Coto)>, ServiceError> {
        self.get(move |ds| ds.all_node_roots()).await
    }

    pub async fn cotonomas_by_prefix(
        &self,
        prefix: String,
        nodes: Option<Vec<Id<Node>>>,
    ) -> Result<Vec<Cotonoma>, ServiceError> {
        self.get(move |ds| {
            ds.cotonomas_by_prefix(&prefix, nodes, DEFAULT_COTONOMAS_BY_PREFIX_LIMIT)
        })
        .await
    }

    pub async fn cotonoma(&self, id: Id<Cotonoma>) -> Result<Cotonoma, ServiceError> {
        self.get(move |ds| ds.try_get_cotonoma(&id)).await
    }

    pub async fn cotonoma_pair(&self, id: Id<Cotonoma>) -> Result<(Cotonoma, Coto), ServiceError> {
        self.get(move |ds| ds.try_get_cotonoma_pair(&id)).await
    }

    pub async fn cotonoma_details(
        &self,
        id: Id<Cotonoma>,
    ) -> Result<CotonomaDetails, ServiceError> {
        self.get(move |ds| {
            let (cotonoma, coto) = ds.try_get_cotonoma_pair(&id)?;
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
    ) -> Result<Page<Cotonoma>, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return errors.into_result();
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

    pub async fn post_cotonoma(
        self,
        input: CotonomaInput<'static>,
        post_to: Id<Cotonoma>,
        operator: Arc<Operator>,
    ) -> Result<(Cotonoma, Coto), ServiceError> {
        if let Err(errors) = input.validate() {
            return errors.into_result();
        }
        let post_to = self.cotonoma(post_to).await?;
        self.change(
            post_to.node_id,
            (input, post_to),
            move |ds, (input, post_to)| ds.post_cotonoma(&input, &post_to, operator.as_ref()),
            |parent, (input, post_to)| parent.post_cotonoma(input, post_to.uuid),
        )
        .await
    }

    pub async fn rename_cotonoma(
        self,
        id: Id<Cotonoma>,
        name: String,
        operator: Arc<Operator>,
    ) -> Result<(Cotonoma, Coto), ServiceError> {
        let cotonoma = self.cotonoma(id).await?;
        self.change(
            cotonoma.node_id,
            (id, name),
            move |ds, (id, name)| ds.rename_cotonoma(&id, &name, operator.as_ref()),
            |parent, (id, name)| parent.rename_cotonoma(id, name),
        )
        .await
    }
}
