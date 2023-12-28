//! Graph (cotos, cotonomas, links) related operations

use std::collections::HashSet;

use diesel::{dsl::sql_query, prelude::*};
use indoc::indoc;

use super::{coto_ops, cotonoma_ops, link_ops};
use crate::{
    db::op::*,
    models::{coto::Coto, graph::Graph, link::Link, node::Node, Id},
    schema::{cotos, links},
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
        let mut level = HashSet::new();
        level.insert(graph.root().uuid);
        while !level.is_empty() {
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

            // Links in the level
            let links = links::table
                .filter(links::source_coto_id.eq_any(&level))
                .load::<Link>(conn)?;
            level.clear();
            for link in links.into_iter() {
                if !graph.contains(&link.target_coto_id) {
                    level.insert(link.target_coto_id);
                }
                graph.add_link(link);
            }
        }
        Ok(graph)
    })
}

/// Experimental implementation of graph traversal by recursive CTE (Common Table Expression).
pub(crate) fn traverse_by_recursive_cte<Conn: AsReadableConn>(
    root: Coto,
    until_cotonoma: bool,
) -> impl Operation<Conn, Graph> {
    read_op(move |conn| {
        let traversed_links: Vec<TraversedLink> = sql_query(indoc! {"
            WITH RECURSIVE traversed_links(id, target, to_cotonoma) AS (
                SELECT NULL, ?, FALSE
                UNION
                SELECT links.uuid, cotos.uuid, cotos.is_cotonoma
                FROM links
                    JOIN traversed_links ON links.source_coto_id = traversed_links.target
                    JOIN cotos ON links.target_coto_id = cotos.uuid
                WHERE ? OR traversed_links.to_cotonoma == FALSE
            )
            SELECT id, target FROM traversed_links WHERE target != ?;
        "})
        .bind::<diesel::sql_types::Text, _>(root.uuid)
        .bind::<diesel::sql_types::Bool, _>(!until_cotonoma)
        .bind::<diesel::sql_types::Text, _>(root.uuid)
        .get_results(conn)?;

        let (mut coto_ids, mut link_ids) = (HashSet::new(), HashSet::new());
        for traversed_link in traversed_links {
            coto_ids.insert(traversed_link.target);
            link_ids.insert(traversed_link.id);
        }

        let cotos = cotos::table
            .filter(cotos::uuid.eq_any(&coto_ids))
            .load::<Coto>(conn)?;
        let links = links::table
            .filter(links::uuid.eq_any(&link_ids))
            .load::<Link>(conn)?;

        let mut graph = Graph::new(root);
        for coto in cotos.into_iter() {
            graph.add_coto(coto);
        }
        for link in links.into_iter() {
            graph.add_link(link);
        }
        Ok(graph)
    })
}

#[derive(Debug, QueryableByName)]
struct TraversedLink {
    #[diesel(sql_type = diesel::sql_types::Text)]
    id: Id<Link>,

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
        let links_updated = link_ops::change_owner_node(from, to).run(ctx)?;
        Ok(cotos_updated + cotonomas_updated + links_updated)
    })
}
