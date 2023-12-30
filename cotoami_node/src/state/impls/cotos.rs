use std::sync::Arc;

use anyhow::{bail, Result};
use cotoami_db::prelude::*;
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

    pub(crate) async fn post_coto(
        self,
        content: String,
        summary: Option<String>,
        cotonoma_id: Id<Cotonoma>,
        operator: Arc<Operator>,
    ) -> Result<Coto> {
        spawn_blocking(move || {
            let mut ds = self.db().new_session()?;

            // Local node or remote node?
            let (cotonoma, _) = ds.cotonoma_or_err(&cotonoma_id)?;
            if !self.db().globals().is_local(&cotonoma) {
                if let Some(parent_service) = self.parent_service(&cotonoma.node_id) {
                    // TODO: post to the parent
                } else {
                    bail!(
                        "Couldn't find a parent node to which the cotonoma [{}] belongs.",
                        cotonoma.name
                    );
                }
            }

            // Post a coto
            let (coto, change) =
                ds.post_coto(&content, summary.as_deref(), &cotonoma, operator.as_ref())?;
            self.pubsub().publish_change(change);

            Ok(coto)
        })
        .await?
    }
}
