//! Graph (cotos, cotonomas, itos) related operations

use std::collections::HashSet;

use diesel::{dsl::sql_query, prelude::*};
use indoc::indoc;

use super::{coto_ops, cotonoma_ops, ito_ops};
use crate::{
    db::op::*,
    models::{coto::Coto, graph::Graph, ito::Ito, node::Node, Id},
    schema::{cotos, itos},
};

/// Breadth first traversal by iterating a query for [Coto]s in the same level.
///
/// The traversal starts at a given [Coto] and only traverses [Coto]s reachable from it.
/// It won't traverse beyond other [Cotonoma]s if `until_cotonoma` is set to `true`.
pub(crate) fn traverse_by_level_queries<Conn: AsReadableConn>(
    root: Coto,
    until_cotonoma: bool,
) -> impl Operation<Conn, Graph> {
    read_op(move |conn| {
        let mut graph = Graph::new(root);
        let mut level: HashSet<Id<Coto>> = HashSet::new();
        level.insert(graph.root().uuid);
        loop {
            // Itos to the next level
            let itos = itos::table
                .filter(itos::source_coto_id.eq_any(&level))
                .load::<Ito>(conn)?;

            // Coto IDs in the next level
            level.clear();
            for ito in itos.into_iter() {
                if !graph.contains(&ito.target_coto_id) {
                    level.insert(ito.target_coto_id);
                }
                graph.add_ito(ito);
            }
            if level.is_empty() {
                break;
            }

            // Cotos in the level
            let cotos = cotos::table
                .filter(cotos::uuid.eq_any(&level))
                .load::<Coto>(conn)?;
            for coto in cotos.into_iter() {
                // Stop traversing the route upon finding a cotonoma
                if until_cotonoma && !graph.is_root(&coto.uuid) && coto.is_cotonoma {
                    level.remove(&coto.uuid);
                }
                graph.add_coto(coto);
            }
            if level.is_empty() {
                break;
            }
        }
        graph.sort_itos();
        Ok(graph)
    })
}

/// Experimental implementation of graph traversal by recursive CTE (Common Table Expression).
pub(crate) fn traverse_by_recursive_cte<Conn: AsReadableConn>(
    root: Coto,
    until_cotonoma: bool,
) -> impl Operation<Conn, Graph> {
    read_op(move |conn| {
        let traversed_itos: Vec<TraversedIto> = sql_query(indoc! {"
            WITH RECURSIVE traversed_itos(id, target, to_cotonoma) AS (
                SELECT NULL, ?, FALSE
                UNION
                SELECT itos.uuid, cotos.uuid, cotos.is_cotonoma
                FROM itos
                    JOIN traversed_itos ON itos.source_coto_id = traversed_itos.target
                    JOIN cotos ON itos.target_coto_id = cotos.uuid
                WHERE ? OR traversed_itos.to_cotonoma == FALSE
            )
            SELECT id, target FROM traversed_itos WHERE target != ?;
        "})
        .bind::<diesel::sql_types::Text, _>(root.uuid)
        .bind::<diesel::sql_types::Bool, _>(!until_cotonoma)
        .bind::<diesel::sql_types::Text, _>(root.uuid)
        .get_results(conn)?;

        let (mut coto_ids, mut ito_ids) = (HashSet::new(), HashSet::new());
        for traversed_ito in traversed_itos {
            coto_ids.insert(traversed_ito.target);
            ito_ids.insert(traversed_ito.id);
        }

        let cotos = cotos::table
            .filter(cotos::uuid.eq_any(&coto_ids))
            .load::<Coto>(conn)?;
        let itos = itos::table
            .filter(itos::uuid.eq_any(&ito_ids))
            .load::<Ito>(conn)?;

        let mut graph = Graph::new(root);
        for coto in cotos.into_iter() {
            graph.add_coto(coto);
        }
        for ito in itos.into_iter() {
            graph.add_ito(ito);
        }
        graph.sort_itos();
        Ok(graph)
    })
}

#[derive(Debug, QueryableByName)]
struct TraversedIto {
    #[diesel(sql_type = diesel::sql_types::Text)]
    id: Id<Ito>,

    #[diesel(sql_type = diesel::sql_types::Text)]
    target: Id<Coto>,
}

pub(crate) fn change_owner_node<'a>(
    from: &'a Id<Node>,
    to: &'a Id<Node>,
) -> impl Operation<WritableConn, usize> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let cotos_updated = coto_ops::change_owner_node(from, to).run(ctx)?;
        let cotonomas_updated = cotonoma_ops::change_owner_node(from, to).run(ctx)?;
        let itos_updated = ito_ops::change_owner_node(from, to).run(ctx)?;
        Ok(cotos_updated + cotonomas_updated + itos_updated)
    })
}
