use std::sync::Arc;

use cotoami_db::prelude::*;
use validator::Validate;

use super::NodeServiceExt;
use crate::{
    service::{error::IntoServiceResult, ServiceError},
    state::NodeState,
};

impl NodeState {
    pub async fn ito(&self, id: Id<Ito>) -> Result<Ito, ServiceError> {
        self.get(move |ds| ds.try_get_ito(&id)).await
    }

    pub async fn outgoing_itos(&self, coto_id: Id<Coto>) -> Result<Vec<Ito>, ServiceError> {
        self.get(move |ds| ds.outgoing_itos(&[coto_id])).await
    }

    pub async fn create_ito(
        self,
        input: ItoInput<'static>,
        operator: Arc<Operator>,
    ) -> Result<Ito, ServiceError> {
        if let Err(errors) = input.validate() {
            return errors.into_result();
        }
        let local_node_id = self.try_get_local_node_id()?;
        let target_node_id = self
            .determine_ito_node(input.source_coto_id, input.target_coto_id, local_node_id)
            .await?;
        self.change(
            target_node_id,
            input,
            move |ds, input| ds.create_ito(&input, operator.as_ref()),
            |parent, input| parent.create_ito(input),
        )
        .await
    }

    async fn determine_ito_node<'a>(
        &self,
        source: Id<Coto>,
        target: Id<Coto>,
        local_node_id: Id<Node>,
    ) -> Result<Id<Node>, ServiceError> {
        self.get(move |ds| ds.determine_ito_node(&source, &target, &local_node_id))
            .await
    }

    pub async fn edit_ito(
        self,
        id: Id<Ito>,
        diff: ItoContentDiff<'static>,
        operator: Arc<Operator>,
    ) -> Result<Ito, ServiceError> {
        if let Err(errors) = diff.validate() {
            return errors.into_result();
        }
        let ito = self.ito(id).await?;
        self.change(
            ito.node_id,
            diff,
            move |ds, diff| ds.edit_ito(&id, diff, operator.as_ref()),
            |parent, diff| parent.edit_ito(id, diff),
        )
        .await
    }

    pub async fn change_ito_order(
        self,
        id: Id<Ito>,
        new_order: i32,
        operator: Arc<Operator>,
    ) -> Result<Ito, ServiceError> {
        let ito = self.ito(id).await?;
        self.change(
            ito.node_id,
            new_order,
            move |ds, new_order| ds.change_ito_order(&id, new_order, operator.as_ref()),
            |parent, new_order| parent.change_ito_order(id, new_order),
        )
        .await
    }

    pub async fn delete_ito(
        self,
        id: Id<Ito>,
        operator: Arc<Operator>,
    ) -> Result<Id<Ito>, ServiceError> {
        let ito = self.ito(id).await?;
        self.change(
            ito.node_id,
            id,
            move |ds, ito_id| {
                let changelog = ds.delete_ito(&ito_id, operator.as_ref())?;
                Ok((ito_id, changelog))
            },
            |parent, ito_id| parent.delete_ito(ito_id),
        )
        .await
    }
}
