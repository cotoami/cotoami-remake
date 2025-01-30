use std::sync::Arc;

use cotoami_db::prelude::*;
use validator::Validate;

use super::NodeServiceExt;
use crate::{
    service::{error::IntoServiceResult, ServiceError},
    state::NodeState,
};

impl NodeState {
    pub async fn link(&self, id: Id<Link>) -> Result<Link, ServiceError> {
        self.get(move |ds| ds.try_get_link(&id)).await
    }

    pub async fn connect(
        self,
        input: LinkInput<'static>,
        operator: Arc<Operator>,
    ) -> Result<Link, ServiceError> {
        if let Err(errors) = input.validate() {
            return errors.into_result();
        }
        let source_coto = self.coto(input.source_coto_id).await?;
        self.change(
            source_coto.node_id,
            input,
            move |ds, input| ds.connect(&input, operator.as_ref()),
            |parent, input| parent.connect(input),
        )
        .await
    }

    pub async fn edit_link(
        self,
        id: Id<Link>,
        diff: LinkContentDiff<'static>,
        operator: Arc<Operator>,
    ) -> Result<Link, ServiceError> {
        if let Err(errors) = diff.validate() {
            return errors.into_result();
        }
        let link = self.link(id).await?;
        self.change(
            link.node_id,
            diff,
            move |ds, diff| ds.edit_link(&id, diff, operator.as_ref()),
            |parent, diff| parent.edit_link(id, diff),
        )
        .await
    }

    pub async fn change_link_order(
        self,
        id: Id<Link>,
        new_order: i32,
        operator: Arc<Operator>,
    ) -> Result<Link, ServiceError> {
        let link = self.link(id).await?;
        self.change(
            link.node_id,
            new_order,
            move |ds, new_order| ds.change_link_order(&id, new_order, operator.as_ref()),
            |parent, new_order| unimplemented!(),
        )
        .await
    }

    pub async fn disconnect(
        self,
        id: Id<Link>,
        operator: Arc<Operator>,
    ) -> Result<Id<Link>, ServiceError> {
        let link = self.link(id).await?;
        self.change(
            link.node_id,
            id,
            move |ds, link_id| {
                let changelog = ds.disconnect(&link_id, operator.as_ref())?;
                Ok((link_id, changelog))
            },
            |parent, link_id| parent.disconnect(link_id),
        )
        .await
    }
}
