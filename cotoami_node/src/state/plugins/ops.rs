use std::str::FromStr;

use anyhow::{bail, Result};
use cotoami_db::prelude::*;

use crate::state::{plugins::PluginError, NodeState};

impl NodeState {
    pub fn plugin_post_coto(
        &self,
        input: CotoInput<'_>,
        post_to: Id<Cotonoma>,
        identifier: &str,
    ) -> Result<Coto> {
        let opr = self.try_get_agent(identifier)?;
        let ds = self.db().new_session()?;
        let (coto, log) = ds.post_coto(&input, &post_to, &opr)?;
        self.pubsub().publish_change(log);
        Ok(coto)
    }

    fn try_get_agent(&self, identifier: &str) -> Result<Operator> {
        if let Some(node_id) = self.read_plugins().configs().agent_node_id(identifier) {
            Ok(Operator::Agent(Id::from_str(&node_id)?))
        } else {
            bail!(PluginError::MissingAgentConfig(identifier.into()))
        }
    }
}
