use std::sync::Arc;

use anyhow::{bail, Result};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::{
    service::{models::Pagination, NodeServiceExt},
    state::NodeState,
};

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
        post_to: Id<Cotonoma>,
        operator: Arc<Operator>,
    ) -> Result<Coto> {
        // Try to post the coto in the local node.
        let post_result = spawn_blocking({
            let this = self.clone();
            move || {
                let mut ds = this.db().new_session()?;

                // Target cotonoma
                let (cotonoma, _) = ds.cotonoma_or_err(&post_to)?;

                // Post the coto if the cotonoma belongs to the local node.
                if this.db().globals().is_local(&cotonoma) {
                    let (coto, change) =
                        ds.post_coto(&content, summary.as_deref(), &cotonoma, operator.as_ref())?;
                    this.pubsub().publish_change(change);
                    Ok::<_, anyhow::Error>(PostResult::Posted(coto))
                } else {
                    Ok::<_, anyhow::Error>(PostResult::ToForward {
                        cotonoma,
                        content,
                        summary,
                    })
                }
            }
        })
        .await??;

        match post_result {
            PostResult::Posted(coto) => Ok(coto),
            PostResult::ToForward {
                cotonoma,
                content,
                summary,
            } => {
                if let Some(mut parent_service) = self.parent_service(&cotonoma.node_id) {
                    parent_service.post_coto(content, summary, post_to).await
                } else {
                    bail!(
                        "Couldn't find a parent node to which the target cotonoma [{}] belongs.",
                        cotonoma.name
                    );
                }
            }
        }
    }
}

enum PostResult {
    Posted(Coto),
    ToForward {
        cotonoma: Cotonoma,
        content: String,
        summary: Option<String>,
    },
}
