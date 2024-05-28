use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use futures::future::FutureExt;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{CotonomaDetails, CotonomaInput, Pagination},
        NodeServiceExt, ServiceError,
    },
    state::NodeState,
};

const DEFAULT_SUB_PAGE_SIZE: i64 = 10;
const DEFAULT_RECENT_PAGE_SIZE: i64 = 100;

impl NodeState {
    pub(crate) async fn cotonoma(&self, id: Id<Cotonoma>) -> Result<Cotonoma, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let (cotonoma, _) = db.new_session()?.try_get_cotonoma(&id)?;
            Ok::<_, anyhow::Error>(cotonoma)
        })
        .await?
        .map_err(ServiceError::from)
    }

    pub(crate) async fn cotonoma_details(
        &self,
        id: Id<Cotonoma>,
    ) -> Result<CotonomaDetails, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;
            let (cotonoma, coto) = ds.try_get_cotonoma(&id)?;
            let supers = ds.super_cotonomas(&coto)?;
            let subs = ds.sub_cotonomas(&cotonoma.uuid, DEFAULT_SUB_PAGE_SIZE, 0)?;
            Ok::<_, anyhow::Error>(CotonomaDetails::new(cotonoma, coto, supers, subs))
        })
        .await?
        .map_err(ServiceError::from)
    }

    pub(crate) async fn cotonoma_by_name(
        &self,
        name: String,
        node: Id<Node>,
    ) -> Result<Cotonoma, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let (cotonoma, _) = db.new_session()?.try_get_cotonoma_by_name(&name, &node)?;
            Ok::<_, anyhow::Error>(cotonoma)
        })
        .await?
        .map_err(ServiceError::from)
    }

    pub(crate) async fn sub_cotonomas(
        &self,
        id: Id<Cotonoma>,
        pagination: Pagination,
    ) -> Result<Paginated<Cotonoma>, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return ("sub_cotonomas", errors).into_result();
        }
        let db = self.db().clone();
        spawn_blocking(move || {
            db.new_session()?.sub_cotonomas(
                &id,
                pagination.page_size.unwrap_or(DEFAULT_SUB_PAGE_SIZE),
                pagination.page,
            )
        })
        .await?
        .map_err(ServiceError::from)
    }

    pub(crate) async fn recent_cotonomas(
        &self,
        node: Option<Id<Node>>,
        pagination: Pagination,
    ) -> Result<Paginated<Cotonoma>, ServiceError> {
        if let Err(errors) = pagination.validate() {
            return ("recent_cotonomas", errors).into_result();
        }
        let db = self.db().clone();
        spawn_blocking(move || {
            db.new_session()?.recent_cotonomas(
                node.as_ref(),
                pagination.page_size.unwrap_or(DEFAULT_RECENT_PAGE_SIZE),
                pagination.page,
            )
        })
        .await?
        .map_err(ServiceError::from)
    }

    pub(crate) async fn post_cotonoma(
        self,
        input: CotonomaInput,
        post_to: Id<Cotonoma>,
        operator: Arc<Operator>,
    ) -> Result<(Cotonoma, Coto), ServiceError> {
        if let Err(errors) = input.validate() {
            return ("post_cotonoma", errors).into_result();
        }
        self.change_in_cotonoma(
            input,
            post_to,
            operator,
            |ds, input, cotonoma, opr| {
                ds.post_cotonoma(
                    &input.name.unwrap_or_else(|| unreachable!()),
                    &cotonoma,
                    opr,
                )
            },
            |parent, input, cotonoma| parent.post_cotonoma(input, cotonoma.uuid).boxed(),
        )
        .await
    }
}
