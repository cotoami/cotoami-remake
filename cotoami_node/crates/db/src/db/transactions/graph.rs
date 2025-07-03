use anyhow::Result;
use diesel::sqlite::SqliteConnection;

use crate::{
    db::{
        op::*,
        ops::{coto_ops, graph_ops, ito_ops},
        DatabaseSession,
    },
    models::prelude::*,
};

impl DatabaseSession<'_> {
    pub fn graph(&mut self, root: Coto, until_cotonoma: bool) -> Result<Graph> {
        self.read_transaction(graph_ops::traverse_by_level_queries(root, until_cotonoma))
    }

    pub fn graph_by_cte(&mut self, root: Coto, until_cotonoma: bool) -> Result<Graph> {
        self.read_transaction(graph_ops::traverse_by_recursive_cte(root, until_cotonoma))
    }

    pub fn incoming_neighbors(&mut self, coto_id: &Id<Coto>) -> Result<(Vec<Ito>, Vec<Coto>)> {
        self.read_transaction(|ctx: &mut Context<'_, SqliteConnection>| {
            let itos = ito_ops::incoming(coto_id).run(ctx)?;
            let source_coto_ids: Vec<Id<Coto>> =
                itos.iter().map(|ito| ito.source_coto_id).collect();
            let cotos = coto_ops::get_by_ids(&source_coto_ids).run(ctx)?;
            Ok((itos, cotos))
        })
    }

    pub fn ancestors_of(&mut self, coto_id: &Id<Coto>) -> Result<Vec<(Vec<Ito>, Vec<Coto>)>> {
        self.read_transaction(graph_ops::ancestors_of(coto_id))
    }
}
