//! Graph (cotos, cotonomas, links) related operations

use std::collections::HashSet;

use diesel::prelude::*;

use super::{coto_ops, cotonoma_ops, link_ops};
use crate::{
    db::op::*,
    models::{coto::Coto, cotonoma::Cotonoma, graph::Graph, link::Link, node::Node, Id},
    schema::{cotos, links},
};

pub(crate) fn get<Conn: AsReadableConn>(
    root: Cotonoma,
    until_cotonoma: bool,
) -> impl Operation<Conn, Graph> {
    read_op(move |conn| {
        let mut graph = Graph::new(root);
        let mut layer = HashSet::new();
        layer.insert(graph.root().coto_id);
        while !layer.is_empty() {
            // Cotos in the layer
            let layer_cotos = cotos::table
                .filter(cotos::uuid.eq_any(&layer))
                .load::<Coto>(conn)?;
            for coto in layer_cotos.into_iter() {
                // Stop traversing the route upon finding a cotonoma
                if until_cotonoma && !graph.is_root(&coto.uuid) && coto.is_cotonoma {
                    layer.remove(&coto.uuid);
                }
                graph.add_coto(coto);
            }

            // Links in the layer
            let layer_links = links::table
                .filter(links::source_coto_id.eq_any(&layer))
                .load::<Link>(conn)?;
            layer.clear();
            for link in layer_links.into_iter() {
                if !graph.contains(&link.target_coto_id) {
                    layer.insert(link.target_coto_id);
                }
                graph.add_link(link);
            }
        }
        Ok(graph)
    })
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
