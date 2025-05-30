use std::collections::HashMap;

use anyhow::Result;
use chrono::NaiveDateTime;
use diesel::sqlite::SqliteConnection;

use crate::{
    db::{
        op::*,
        ops::{
            changelog_ops, coto_ops, node_ops, node_role_ops,
            node_role_ops::{child_ops, parent_ops},
        },
        DatabaseSession,
    },
    models::prelude::*,
};

pub mod children;
pub mod clients;
pub mod local;
pub mod parents;
pub mod servers;

impl DatabaseSession<'_> {
    pub fn node(&mut self, node_id: &Id<Node>) -> Result<Option<Node>> {
        self.read_transaction(node_ops::get(node_id))
    }

    pub fn try_get_node(&mut self, node_id: &Id<Node>) -> Result<Node> {
        self.read_transaction(node_ops::try_get(node_id))?
            .map_err(anyhow::Error::from)
    }

    pub fn all_nodes(&mut self) -> Result<Vec<Node>> { self.read_transaction(node_ops::all()) }

    /// Import a node data sent from another node.
    ///
    /// This operation occurs during initializing a connection between two nodes
    /// as they exchange their node info before the parent starts sending changelogs
    /// to the child.
    ///
    /// A change will be made only when:
    /// 1. The given node does not exist in the database (INSERT).
    /// 2. The ID of the given node already exists in the database and the version of
    ///    the given node is larger than the existing one (UPDATE).
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
            return Ok(Some(Operator::LocalNode(node_id)));
        }
        if let Some(child) = self.read_transaction(child_ops::get(&node_id))? {
            return Ok(Some(Operator::ChildNode(child)));
        }
        Ok(None)
    }

    /// Returns a map from node ID to the timestamp of the most recent post
    /// made by other nodes (excluding the local node).
    /// The target nodes are all parent nodes and the local node.
    pub fn others_last_posted_at(
        &mut self,
        operator: &Operator,
    ) -> Result<HashMap<Id<Node>, NaiveDateTime>> {
        operator.requires_to_be_owner()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.read_transaction(|ctx: &mut Context<'_, SqliteConnection>| {
            let mut map = parent_ops::others_last_posted_at(&local_node_id).run(ctx)?;
            if let Some(in_local) =
                coto_ops::others_last_posted_at_in_local(&local_node_id).run(ctx)?
            {
                map.insert(local_node_id, in_local);
            }
            Ok(map)
        })
    }

    pub fn mark_all_as_read(
        &mut self,
        read_at: Option<NaiveDateTime>,
        operator: &Operator,
    ) -> Result<NaiveDateTime> {
        operator.requires_to_be_owner()?;
        let read_at = read_at.unwrap_or_else(crate::current_datetime);
        self.mark_local_as_read(read_at, operator)?;
        self.mark_all_parents_as_read(read_at, operator)?;
        Ok(read_at)
    }

    pub fn mark_as_read(
        &mut self,
        node_id: &Id<Node>,
        read_at: Option<NaiveDateTime>,
        operator: &Operator,
    ) -> Result<NaiveDateTime> {
        operator.requires_to_be_owner()?;
        let read_at = read_at.unwrap_or_else(crate::current_datetime);
        if self.globals.is_local_node(node_id) {
            self.mark_local_as_read(read_at, operator)?;
        } else {
            self.mark_parent_as_read(node_id, read_at, operator)?;
        }
        Ok(read_at)
    }
}
