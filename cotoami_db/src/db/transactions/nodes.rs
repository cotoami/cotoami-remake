use std::collections::HashMap;

use anyhow::Result;

use crate::{
    db::{
        op::*,
        ops::{changelog_ops, node_ops, node_role_ops, node_role_ops::child_ops},
        DatabaseSession,
    },
    models::prelude::*,
};

pub mod child;
pub mod client;
pub mod local;
pub mod parent;
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

    pub fn database_role_of(&mut self, node_id: &Id<Node>) -> Result<Option<DatabaseRole>> {
        self.read_transaction(node_role_ops::database_role_of(node_id))
    }

    pub fn database_roles_of(
        &mut self,
        node_ids: &Vec<Id<Node>>,
    ) -> Result<HashMap<Id<Node>, DatabaseRole>> {
        self.read_transaction(node_role_ops::database_roles_of(node_ids))
    }

    pub fn as_operator(&mut self, node_id: Id<Node>) -> Result<Option<Operator>> {
        if node_id == self.globals.try_get_local_node_id()? {
            return Ok(Some(Operator::Owner(node_id)));
        }
        if let Some(child) = self.read_transaction(child_ops::get(&node_id))? {
            return Ok(Some(Operator::ChildNode(child)));
        }
        Ok(None)
    }
}
