use anyhow::Result;

use crate::{
    db::{
        ops::{node_role_ops::child_ops, Paginated},
        DatabaseSession,
    },
    models::prelude::*,
};

impl<'a> DatabaseSession<'a> {
    pub fn recent_child_nodes(
        &mut self,
        page_size: i64,
        page_index: i64,
        operator: &Operator,
    ) -> Result<Paginated<(ChildNode, Node)>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(child_ops::recent_pairs(page_size, page_index))
    }

    pub fn edit_child_node(
        &self,
        id: &Id<Node>,
        as_owner: bool,
        can_edit_links: bool,
    ) -> Result<ChildNode> {
        self.write_transaction(child_ops::edit(id, as_owner, can_edit_links))
    }
}
