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
        let mut next: HashSet<Id<Coto>> = [graph.root().uuid].into();
        loop {
            // Next itos
            let itos = itos::table
                .filter(itos::source_coto_id.eq_any(&next))
                .load::<Ito>(conn)?;

            // Next coto IDs
            next.clear();
            for ito in itos.into_iter() {
                if !graph.contains(&ito.target_coto_id) {
                    next.insert(ito.target_coto_id); // unvisited
                }
                graph.add_ito(ito);
            }
            if next.is_empty() {
                break;
            }

            // Next cotos
            let cotos = cotos::table
                .filter(cotos::uuid.eq_any(&next))
                .load::<Coto>(conn)?;
            for coto in cotos.into_iter() {
                // Stop traversing upon finding a cotonoma
                if until_cotonoma && coto.is_cotonoma {
                    next.remove(&coto.uuid);
                }
                graph.add_coto(coto);
            }
            if next.is_empty() {
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

pub(crate) fn ancestors_of<Conn: AsReadableConn>(
    coto_id: &Id<Coto>,
) -> impl Operation<Conn, Vec<(Vec<Ito>, Vec<Coto>)>> + '_ {
    read_op(move |conn| {
        let mut ancestors = Vec::new();
        let mut traversed: HashSet<Id<Coto>> = [*coto_id].into();
        let mut next: HashSet<Id<Coto>> = [*coto_id].into();
        loop {
            // Next itos
            let itos: Vec<Ito> = itos::table
                .filter(itos::target_coto_id.eq_any(&next))
                .load::<Ito>(conn)?;
            if itos.is_empty() {
                break;
            }

            // Next coto IDs
            next = itos
                .iter()
                .filter_map(|ito| {
                    if !traversed.contains(&ito.source_coto_id) {
                        Some(ito.source_coto_id) // unvisited
                    } else {
                        None
                    }
                })
                .collect();
            traversed.extend(&next);

            // Next cotos
            let cotos = cotos::table
                .filter(cotos::uuid.eq_any(&next))
                .load::<Coto>(conn)?;
            ancestors.push((itos, cotos));

            if next.is_empty() {
                break;
            }
        }
        Ok(ancestors)
    })
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
