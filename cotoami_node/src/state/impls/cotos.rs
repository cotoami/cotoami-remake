use anyhow::Result;
use cotoami_db::prelude::{Coto, Cotonoma, Id, Paginated};
use tokio::task::spawn_blocking;

use crate::{service::models::Pagination, state::NodeState};

const DEFAULT_PAGE_SIZE: i64 = 30;

impl NodeState {
    pub(crate) async fn recent_cotos(
        &self,
        cotonoma: Option<Id<Cotonoma>>,
        pagination: Pagination,
    ) -> Result<Paginated<Coto>> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;
            ds.recent_cotos(
                None,
                cotonoma.as_ref(),
                pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
                pagination.page,
            )
        })
        .await?
    }
}
