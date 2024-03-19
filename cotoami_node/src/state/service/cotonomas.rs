use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{
    service::{models::Pagination, ServiceError},
    state::NodeState,
};

const DEFAULT_PAGE_SIZE: i64 = 100;

impl NodeState {
    pub(crate) async fn recent_cotonomas(
        &self,
        node: Option<Id<Node>>,
        pagination: Pagination,
    ) -> Result<Paginated<Cotonoma>, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;
            ds.recent_cotonomas(
                node.as_ref(),
                pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
                pagination.page,
            )
        })
        .await?
        .map_err(ServiceError::from)
    }
}
