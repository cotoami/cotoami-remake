use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{
    service::{
        models::{CotonomaDetails, Pagination},
        ServiceError,
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

    pub(crate) async fn sub_cotonomas(
        &self,
        id: Id<Cotonoma>,
        pagination: Pagination,
    ) -> Result<Paginated<Cotonoma>, ServiceError> {
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
}
