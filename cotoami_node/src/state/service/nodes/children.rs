use std::sync::Arc;

use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    service::{ServiceError, error::IntoServiceResult, models::EditChild},
    state::NodeState,
};

impl NodeState {
    pub async fn child_node(
        &self,
        id: Id<Node>,
        operator: Arc<Operator>,
    ) -> Result<ChildNode, ServiceError> {
        self.get(move |ds| ds.try_get_child_node(&id, &operator))
            .await
    }

    pub async fn edit_child(
        &self,
        node_id: Id<Node>,
        values: EditChild,
        operator: Arc<Operator>,
    ) -> Result<ChildNode, ServiceError> {
        if let Err(errors) = values.validate() {
            return errors.into_result();
        }
        spawn_blocking({
            let db = self.db().clone();
            move || {
                db.new_session()?.edit_child_node(
                    &node_id,
                    values.as_owner,
                    values.can_edit_itos,
                    &operator,
                )
            }
        })
        .await?
        .map_err(ServiceError::from)
    }
}
