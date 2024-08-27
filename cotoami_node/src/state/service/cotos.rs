use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use futures::future::FutureExt;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{PaginatedCotos, Pagination},
        NodeServiceExt, ServiceError,
    },
    state::NodeState,
};

const DEFAULT_PAGE_SIZE: i64 = 20;

impl NodeState {
    pub async fn recent_cotos(
        &self,
        node: Option<Id<Node>>,
        cotonoma: Option<Id<Cotonoma>>,
        pagination: Pagination,
    ) -> Result<PaginatedCotos, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return ("recent_cotos", errors).into_result();
        }
        self.get(move |ds| {
            let page = ds.recent_cotos(
                node.as_ref(),
                cotonoma.as_ref(),
                pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
                pagination.page,
            )?;
            PaginatedCotos::new(page, ds)
        })
        .await
    }

    pub async fn search_cotos(
        &self,
        query: String,
        node: Option<Id<Node>>,
        cotonoma: Option<Id<Cotonoma>>,
        pagination: Pagination,
    ) -> Result<PaginatedCotos, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return ("search_cotos", errors).into_result();
        }
        self.get(move |ds| {
            let page = ds.search_cotos(
                &query,
                node.as_ref(),
                cotonoma.as_ref(),
                pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
                pagination.page,
            )?;
            PaginatedCotos::new(page, ds)
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
            return ("post_coto", errors).into_result();
        }
        self.change_in_cotonoma(
            input,
            post_to,
            move |ds, input, cotonoma| ds.post_coto(&input, cotonoma, operator.as_ref()),
            |parent, input, cotonoma| parent.post_coto(input, cotonoma.uuid).boxed(),
        )
        .await
    }
}
