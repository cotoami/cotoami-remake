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
        create_in: Id<Node>,
        operator: Arc<Operator>,
    ) -> Result<Link, ServiceError> {
        if let Err(errors) = input.validate() {
            return errors.into_result();
        }
        self.change(
            create_in,
            input,
            move |ds, input| ds.connect(&input, operator.as_ref()),
            |parent, input| unimplemented!(),
        )
        .await
    }
}
