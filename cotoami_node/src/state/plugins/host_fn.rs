use std::str::FromStr;

use anyhow::{anyhow, bail, Result};
use cotoami_db::prelude::*;

use crate::state::{
    plugins::{convert::*, PluginError},
    NodeState,
};

#[derive(Clone, derive_new::new)]
pub(crate) struct HostFnContext {
    pub plugin_identifier: String,
    pub node_state: NodeState,
}

impl HostFnContext {
    #[allow(dead_code)]
    pub fn post_coto(
        &self,
        input: cotoami_plugin_api::CotoInput,
    ) -> Result<cotoami_plugin_api::Coto> {
        let opr = self.try_get_agent()?;
        let db_input: CotoInput<'_> = as_db_coto_input(&input)?;
        let post_to = self.target_cotonoma_id(input.post_to.as_deref())?;
        let ds = self.node_state.db().new_session()?;
        let (coto, log) = ds.post_coto(&db_input, &post_to, &opr)?;
        self.node_state.pubsub().publish_change(log);
        Ok(into_plugin_coto(coto).unwrap())
    }

    fn try_get_agent(&self) -> Result<Operator> {
        if let Some(node_id) = self
            .node_state
            .read_plugins()
            .configs()
            .agent_node_id(&self.plugin_identifier)
        {
            Ok(Operator::Agent(Id::from_str(&node_id)?))
        } else {
            bail!(PluginError::MissingAgentConfig(
                self.plugin_identifier.clone()
            ))
        }
    }

    fn target_cotonoma_id(&self, cotonoma_id: Option<&str>) -> Result<Id<Cotonoma>> {
        let cotonoma_id = if let Some(ref cotonoma_id) = cotonoma_id {
            Id::from_str(cotonoma_id)?
        } else {
            self.node_state
                .root_cotonoma_id()
                .ok_or(anyhow!("Missing root cotonoma."))?
        };
        Ok(cotonoma_id)
    }
}
