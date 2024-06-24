use anyhow::Result;

use crate::{
    db::{ops::graph_ops, DatabaseSession},
    models::prelude::*,
};

impl<'a> DatabaseSession<'a> {
    pub fn graph(&mut self, root: Coto, until_cotonoma: bool) -> Result<Graph> {
        self.read_transaction(graph_ops::traverse_by_level_queries(root, until_cotonoma))
    }

    pub fn graph_by_cte(&mut self, root: Coto, until_cotonoma: bool) -> Result<Graph> {
        self.read_transaction(graph_ops::traverse_by_recursive_cte(root, until_cotonoma))
    }
}
