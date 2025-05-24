use std::collections::HashMap;

use anyhow::Result;
use chrono::NaiveDateTime;

use crate::{
    db::{
        op::*,
        ops::{changelog_ops, graph_ops, node_role_ops, node_role_ops::parent_ops},
        DatabaseSession,
    },
    models::prelude::*,
};

impl DatabaseSession<'_> {
    pub fn parent_node(&self, id: &Id<Node>, operator: &Operator) -> Result<Option<ParentNode>> {
        operator.requires_to_be_owner()?;
        Ok(self.globals.parent_node(id))
    }

    pub fn last_posted_at_by_others_per_parent(
        &mut self,
        local_node_id: &Id<Node>,
    ) -> Result<HashMap<Id<Node>, Option<NaiveDateTime>>> {
        self.read_transaction(parent_ops::last_posted_at_by_others_map(local_node_id))
    }

    /// Forks the local node from the specified parent node.
    ///
    /// In Cotoami, `fork` means disconnecting from a parent node and taking the ownership of
    /// entities (cotos/cotonomas/itos) owned by the parent until then. It also means that
    /// update requests to those entities won't be relayed to the parent anymore, instead
    /// the local node will handle them.
    ///
    /// Note that target entities by this function are only those that are owned by the specified
    /// parent, not by nodes indirectly connected via the parent. So if the parent has other nodes
    /// as its parent (grand parents), the local node won't receive the changes from those nodes anymore.
    ///
    /// This method is assumed to be used in the following situations:
    /// * Promoting a replica node to a primary/master node.
    /// * When there's a need for an intermediate node to take over the role of its parent node
    ///   because of the outage/retirement of the parent.
    pub fn fork_from(
        &self,
        parent_node_id: &Id<Node>,
        operator: &Operator,
    ) -> Result<(usize, ChangelogEntry)> {
        operator.requires_to_be_owner()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        let mut parent_node = self.globals.try_write_parent_node(parent_node_id)?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            // Set the parent to be forked
            *parent_node = node_role_ops::fork_from(parent_node_id).run(ctx)?;

            // Change the owner of every entity created in the parent node to the local node
            let affected = graph_ops::change_owner_node(parent_node_id, &local_node_id).run(ctx)?;

            let change = Change::ChangeOwnerNode {
                from: *parent_node_id,
                to: local_node_id,
                last_change_number: parent_node.changes_received,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;

            Ok((affected, changelog))
        })
    }
}
