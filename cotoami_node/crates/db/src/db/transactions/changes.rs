use anyhow::Result;

use crate::{
    db::{ops::changelog_ops, DatabaseSession},
    models::prelude::*,
};

impl DatabaseSession<'_> {
    pub fn import_change(
        &self,
        log: &ChangelogEntry,
        parent_node_id: &Id<Node>,
    ) -> Result<Option<ChangelogEntry>> {
        let mut parent_node = self.globals.try_write_parent_node(parent_node_id)?;
        let local_node = self.globals.try_read_local_node()?;
        self.write_transaction(changelog_ops::import_change(
            log,
            &mut parent_node,
            &local_node,
        ))
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
