use std::{collections::HashSet, str::FromStr};

use anyhow::{anyhow, bail, Result};
use cotoami_db::prelude::*;
use cotoami_plugin_api::Ancestors;
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
        post_to: Option<String>,
    ) -> Result<cotoami_plugin_api::Coto> {
        let opr = self.try_get_agent()?;
        let db_input: CotoInput<'_> = as_db_coto_input(&input)?;
        let post_to = self.target_cotonoma_id(post_to.as_deref())?;
        let ds = self.node_state.db().new_session()?;
        let (coto, log) = ds.post_coto(&db_input, &post_to, &opr)?;
        self.node_state.pubsub().publish_change(log);
        Ok(into_plugin_coto(coto).unwrap())
    }

    #[allow(dead_code)]
    pub fn edit_coto(
        &self,
        id: String,
        diff: cotoami_plugin_api::CotoContentDiff,
    ) -> Result<cotoami_plugin_api::Coto> {
        let opr = self.try_get_agent()?;
        let coto_id: Id<Coto> = Id::from_str(&id)?;
        let db_diff = as_db_coto_content_diff(diff);
        let ds = self.node_state.db().new_session()?;
        let (coto, log) = ds.edit_coto(&coto_id, db_diff, &opr)?;
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
    pub fn ancestors_of(&mut self, coto_id: String) -> Result<Ancestors> {
        let coto_id: Id<Coto> = Id::from_str(&coto_id)?;
        let mut ds = self.node_state.db().new_session()?;
        let ancestors = ds.ancestors_of(&coto_id)?;
        let author_ids: HashSet<Id<Node>> = ancestors
            .iter()
            .map(|(itos, cotos)| {
                itos.iter()
                    .map(|ito| ito.created_by_id)
                    .chain(cotos.iter().map(|coto| coto.posted_by_id))
                    .collect::<Vec<Id<Node>>>()
            })
            .flatten()
            .collect();
        let authors = ds
            .nodes_map(&author_ids)?
            .into_iter()
            .map(|(id, node)| (id.to_string(), into_plugin_node(node)))
            .collect();
        let ancestors = ancestors
            .into_iter()
            .map(|(itos, cotos)| {
                (
                    itos.into_iter().map(into_plugin_ito).collect(),
                    cotos.into_iter().filter_map(into_plugin_coto).collect(),
                )
            })
            .collect();
        Ok(Ancestors { ancestors, authors })
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
