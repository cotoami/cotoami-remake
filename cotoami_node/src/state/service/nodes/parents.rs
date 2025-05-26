use std::{collections::HashMap, sync::Arc};

use anyhow::Result;
use chrono::NaiveDateTime;
use cotoami_db::prelude::*;

use crate::{service::ServiceError, state::NodeState};

impl NodeState {
    pub async fn parent_nodes(
        &self,
        operator: Arc<Operator>,
    ) -> Result<Vec<ParentNode>, ServiceError> {
        self.get(move |ds| ds.parent_nodes(&operator)).await
    }

    pub async fn others_last_posted_at(
        &self,
        operator: Arc<Operator>,
    ) -> Result<HashMap<Id<Node>, Option<NaiveDateTime>>, ServiceError> {
        self.get(move |ds| ds.others_last_posted_at(&operator))
            .await
    }
}
