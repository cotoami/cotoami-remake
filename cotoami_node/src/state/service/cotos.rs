use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use serde_json::value::Value;
use tokio::task::spawn_blocking;

use crate::{
    service::{
        error::{IntoServiceResult, RequestError},
        models::{Cotos, Pagination},
        NodeServiceExt, ServiceError,
    },
    state::NodeState,
};

const DEFAULT_PAGE_SIZE: i64 = 30;

impl NodeState {
    pub(crate) async fn recent_cotos(
        &self,
        cotonoma: Option<Id<Cotonoma>>,
        pagination: Pagination,
    ) -> Result<Cotos, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;
            let paginated = ds.recent_cotos(
                None,
                cotonoma.as_ref(),
                pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
                pagination.page,
            )?;
            let posted_in = ds.cotonomas_of(&paginated.rows)?;
            let repost_of_ids: Vec<Id<Coto>> = paginated
                .rows
                .iter()
                .map(|coto| coto.repost_of_id)
                .flatten()
                .collect();
            Ok::<_, anyhow::Error>(Cotos {
                paginated,
                posted_in,
                repost_of: ds.cotos(repost_of_ids)?,
            })
        })
        .await?
        .map_err(ServiceError::from)
    }

    pub(crate) async fn post_coto(
        self,
        content: String,
        summary: Option<String>,
        post_to: Id<Cotonoma>,
        operator: Arc<Operator>,
    ) -> Result<Coto, ServiceError> {
        // Try to post the coto in the local node.
        let post_result = spawn_blocking({
            let this = self.clone();
            move || {
                let mut ds = this.db().new_session()?;

                // Target cotonoma
                let (cotonoma, _) = ds.try_get_cotonoma(&post_to)?;

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
                    parent_service
                        .post_coto(content, summary, post_to)
                        .await
                        .map_err(ServiceError::from)
                } else {
                    RequestError::new("posting-to-inaccessible-cotonoma")
                        .with_param("cotonoma-name", Value::String(cotonoma.name))
                        .into_result()
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
