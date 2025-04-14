use anyhow::Result;

use crate::{
    db::{
        ops::{node_role_ops::child_ops, Page},
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
    ) -> Result<Page<(ChildNode, Node)>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(child_ops::recent_pairs(page_size, page_index))
    }

    pub fn try_get_child_node(&mut self, id: &Id<Node>, operator: &Operator) -> Result<ChildNode> {
        operator.requires_to_be_owner()?;
        self.read_transaction(child_ops::try_get(id))?
            .map_err(anyhow::Error::from)
    }

    pub fn edit_child_node(
        &self,
        id: &Id<Node>,
        input: &ChildNodeInput,
        operator: &Operator,
    ) -> Result<ChildNode> {
        operator.requires_to_be_owner()?;
        self.write_transaction(child_ops::edit(id, input))
    }
}
