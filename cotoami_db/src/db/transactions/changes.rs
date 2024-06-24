use anyhow::Result;

use crate::{
    db::{ops::changelog_ops, DatabaseSession},
    models::prelude::*,
};

impl<'a> DatabaseSession<'a> {
    pub fn import_change(
        &self,
        log: &ChangelogEntry,
        parent_node_id: &Id<Node>,
    ) -> Result<Option<ChangelogEntry>> {
        let mut parent_node = self.globals.try_write_parent_node(parent_node_id)?;
        self.write_transaction(changelog_ops::import_change(log, &mut parent_node))
    }

    pub fn chunk_of_changes(
        &mut self,
        from: i64,
        limit: i64,
    ) -> Result<(Vec<ChangelogEntry>, i64)> {
        self.read_transaction(changelog_ops::chunk(from, limit))
    }

    pub fn last_change_number(&mut self) -> Result<Option<i64>> {
        self.read_transaction(changelog_ops::last_serial_number())
    }
}
