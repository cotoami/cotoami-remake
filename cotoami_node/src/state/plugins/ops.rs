use std::str::FromStr;

use anyhow::{anyhow, bail, Result};
use cotoami_db::prelude::*;

use crate::state::{
    plugins::{convert::*, PluginError},
    NodeState,
};

impl NodeState {
    pub fn plugin_post_coto(
        &self,
        input: cotoami_plugin_api::CotoInput,
        identifier: &str,
    ) -> Result<Coto> {
        let db_input: CotoInput<'_> = as_db_coto_input(&input)?;
        let post_to: Id<Cotonoma> = if let Some(ref post_to) = input.post_to {
            Id::from_str(post_to)?
        } else {
            self.root_cotonoma_id()
                .ok_or(anyhow!("Missing root cotonoma."))?
        };
        let opr = self.try_get_agent(identifier)?;
        let ds = self.db().new_session()?;
        let (coto, log) = ds.post_coto(&db_input, &post_to, &opr)?;
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
