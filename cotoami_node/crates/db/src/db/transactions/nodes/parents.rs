use anyhow::Result;
use chrono::NaiveDateTime;

use crate::{
    db::{ops::node_role_ops::parent_ops, DatabaseSession},
    models::prelude::*,
};

impl DatabaseSession<'_> {
    pub fn parent_nodes(&mut self, operator: &Operator) -> Result<Vec<ParentNode>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(parent_ops::all())
    }

    pub fn parent_node(&self, id: &Id<Node>, operator: &Operator) -> Result<Option<ParentNode>> {
        operator.requires_to_be_owner()?;
        Ok(self.globals.parent_node(id))
    }

    pub fn mark_all_parents_as_read(
        &self,
        read_at: NaiveDateTime,
        operator: &Operator,
    ) -> Result<()> {
        operator.requires_to_be_owner()?;
        let parents = self.write_transaction(parent_ops::mark_all_as_read(read_at))?;
        self.globals.replace_parent_nodes(parents);
        Ok(())
    }

    pub fn mark_parent_as_read(
        &self,
        id: &Id<Node>,
        read_at: NaiveDateTime,
        operator: &Operator,
    ) -> Result<()> {
        operator.requires_to_be_owner()?;
        let parent = self.write_transaction(parent_ops::mark_as_read(id, read_at))?;
        self.globals.cache_parent_node(parent);
        Ok(())
    }
}
