use anyhow::Result;

use crate::{
    db::{
        op::*,
        ops::{changelog_ops, node_ops, node_role_ops},
        DatabaseSession,
    },
    models::prelude::*,
};

pub mod local;
pub mod server;

impl<'a> DatabaseSession<'a> {
    pub fn node(&mut self, node_id: &Id<Node>) -> Result<Option<Node>> {
        self.read_transaction(node_ops::get(node_id))
    }

    pub fn try_get_node(&mut self, node_id: &Id<Node>) -> Result<Node> {
        self.read_transaction(node_ops::try_get(node_id))?
            .map_err(anyhow::Error::from)
    }

    pub fn all_nodes(&mut self) -> Result<Vec<Node>> { self.read_transaction(node_ops::all()) }

    /// Import a node data sent from another node.
    pub fn import_node(&self, node: &Node) -> Result<Option<(Node, ChangelogEntry)>> {
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            if let Some(node) = node_ops::upsert(node).run(ctx)? {
                let change = Change::UpsertNode(node);
                let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
                let Change::UpsertNode(node) = change else { unreachable!() };
                Ok(Some((node, changelog)))
            } else {
                Ok(None)
            }
        })
    }

    pub fn set_network_disabled(
        &self,
        id: &Id<Node>,
        disabled: bool,
        operator: &Operator,
    ) -> Result<NetworkRole> {
        operator.requires_to_be_owner()?;
        self.write_transaction(node_role_ops::set_network_disabled(id, disabled))
    }
}
