use std::sync::Arc;

use cotoami_db::prelude::*;
use validator::Validate;

use super::NodeServiceExt;
use crate::{
    service::{error::IntoServiceResult, ServiceError},
    state::NodeState,
};

impl NodeState {
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
}
