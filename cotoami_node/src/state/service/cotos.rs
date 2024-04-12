use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use serde_json::value::Value;
use tokio::task::spawn_blocking;

use crate::{
    service::{
        error::{IntoServiceResult, RequestError},
        models::{PaginatedCotos, Pagination},
        NodeServiceExt, ServiceError,
    },
    state::NodeState,
};

const DEFAULT_RECENT_PAGE_SIZE: i64 = 20;

impl NodeState {
    pub(crate) async fn recent_cotos(
        &self,
        node: Option<Id<Node>>,
        cotonoma: Option<Id<Cotonoma>>,
        pagination: Pagination,
    ) -> Result<PaginatedCotos, ServiceError> {
        let db = self.db().clone();
        spawn_blocking(move || {
            let mut ds = db.new_session()?;

            // Recent cotos
            let page = ds.recent_cotos(
                node.as_ref(),
                cotonoma.as_ref(),
                pagination.page_size.unwrap_or(DEFAULT_RECENT_PAGE_SIZE),
                pagination.page,
            )?;

            // Fetch related entities
            let original_ids: Vec<Id<Coto>> = page
                .rows
                .iter()
                .map(|coto| coto.repost_of_id)
                .flatten()
                .collect();
            let originals = ds.cotos(original_ids)?;
            let posted_in = ds.cotonomas_of(page.rows.iter().chain(originals.iter()))?;
            let as_cotonomas = ds.as_cotonomas(page.rows.iter())?;

            Ok::<_, anyhow::Error>(PaginatedCotos {
                page,
                posted_in,
                as_cotonomas,
                originals,
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
