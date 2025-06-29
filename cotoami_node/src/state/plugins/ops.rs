use anyhow::Result;
use cotoami_db::prelude::*;

use crate::state::NodeState;

impl NodeState {
    pub fn plugin_post_coto(
        &self,
        input: CotoInput<'_>,
        post_to: Id<Cotonoma>,
        agent: Id<Node>,
    ) -> Result<Coto> {
        let opr = Operator::Agent(agent);
        let ds = self.db().new_session()?;
        let (coto, log) = ds.post_coto(&input, &post_to, &opr)?;
        self.pubsub().publish_change(log);
        Ok(coto)
    }
}
