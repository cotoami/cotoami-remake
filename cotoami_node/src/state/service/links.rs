use std::sync::Arc;

use cotoami_db::prelude::*;
use validator::Validate;

use crate::{
    service::{error::IntoServiceResult, ServiceError},
    state::NodeState,
};

impl NodeState {
    pub async fn connect(
        self,
        input: LinkInput<'static>,
        created_in: Id<Cotonoma>,
        operator: Arc<Operator>,
    ) -> Result<Link, ServiceError> {
        if let Err(errors) = input.validate() {
            return errors.into_result();
        }
        let (created_in, _) = self.cotonoma(created_in).await?;
        self.change(
            created_in.node_id,
            (input, created_in),
            move |ds, (input, created_in)| ds.connect(&input, Some(&created_in), operator.as_ref()),
            |parent, (input, created_in)| unimplemented!(),
        )
        .await
    }
}
