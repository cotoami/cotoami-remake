use std::str::FromStr;

use anyhow::{anyhow, bail, Result};
use cotoami_db::prelude::*;
use extism::UserData;

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
    pub fn new_user_data(&self) -> UserData<HostFnContext> { UserData::new(self.clone()) }

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

    #[allow(dead_code)]
    pub fn create_ito(
        &self,
        input: cotoami_plugin_api::ItoInput,
    ) -> Result<cotoami_plugin_api::Ito> {
        let opr = self.try_get_agent()?;
        let db_input: ItoInput<'_> = as_db_ito_input(&input)?;
        let ds = self.node_state.db().new_session()?;
        let (ito, log) = ds.create_ito(&db_input, &opr)?;
        self.node_state.pubsub().publish_change(log);
        Ok(into_plugin_ito(ito))
    }

    #[allow(dead_code)]
    pub fn ancestors_of(
        &mut self,
        coto_id: String,
    ) -> Result<Vec<(Vec<cotoami_plugin_api::Ito>, Vec<cotoami_plugin_api::Coto>)>> {
        let coto_id: Id<Coto> = Id::from_str(&coto_id)?;
        let mut ds = self.node_state.db().new_session()?;
        let ancestors = ds
            .ancestors_of(&coto_id)?
            .into_iter()
            .map(|(itos, cotos)| {
                (
                    itos.into_iter().map(into_plugin_ito).collect(),
                    cotos.into_iter().filter_map(into_plugin_coto).collect(),
                )
            })
            .collect();
        Ok(ancestors)
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
