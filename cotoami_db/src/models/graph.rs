//! Graph-related data structure and operations

use std::collections::HashMap;

use petgraph::prelude::{Graph as Petgraph, NodeIndex};

use super::{coto::Coto, link::Link, Id};

/// A graph is a set of cotos that are connected with links
#[derive(Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct Graph {
    /// Root coto ID
    root_id: Id<Coto>,

    /// All the cotos in this graph, each of which is mapped by its ID
    cotos: HashMap<Id<Coto>, Coto>,

    /// All the links in this graph, each of which is mapped by the ID of the source coto
    links: HashMap<Id<Coto>, Vec<Link>>,
}

impl Graph {
    /// Creates an empty graph with a root cotonoma
    pub fn new(root: Coto) -> Self {
        let mut graph = Self {
            root_id: root.uuid,
            cotos: HashMap::new(),
            links: HashMap::new(),
        };
        graph.add_coto(root);
        graph
    }

    pub fn root(&self) -> &Coto {
        self.cotos
            .get(&self.root_id)
            .unwrap_or_else(|| unreachable!())
    }

    pub fn is_root(&self, coto_id: &Id<Coto>) -> bool { self.root_id == *coto_id }

    pub fn add_coto(&mut self, coto: Coto) { self.cotos.insert(coto.uuid, coto); }

    pub fn contains(&self, coto_id: &Id<Coto>) -> bool { self.cotos.contains_key(coto_id) }

    pub fn add_link(&mut self, link: Link) {
        self.links
            .entry(link.source_coto_id)
            .or_insert_with(Vec::new)
            .push(link);
    }

    pub(crate) fn sort_links(&mut self) {
        for links in self.links.values_mut() {
            links.sort_by_key(|link| link.order);
        }
    }

    pub fn assert_links_sorted(&self) {
        for links in self.links.values() {
            let order: Vec<usize> = links.iter().map(|link| link.order as usize).collect();
            let expected: Vec<usize> = (1..=links.len()).collect();
            assert_eq!(order, expected);
        }
    }

    /// Converts this graph into a petgraph's [petgraph::graph::Graph].
    ///
    /// You can use the `sort` flag to get cotos and links in a predictable order.
    pub fn into_petgraph(self, sort: bool) -> Petgraph<Coto, Link> {
        let mut petgraph = Petgraph::<Coto, Link>::new();

        // Cotos
        let mut cotos: Vec<Coto> = self.cotos.into_values().collect();
        if sort {
            cotos.sort_by_key(|coto| {
                // `rowid` can't be used here because it won't be deserialized.
                coto.created_at
            });
        }
        let mut node_indices: HashMap<Id<Coto>, NodeIndex> = HashMap::new();
        for coto in cotos.into_iter() {
            let coto_id = coto.uuid;
            let node_index = petgraph.add_node(coto);
            node_indices.insert(coto_id, node_index);
        }

        // Links
        let mut links: Vec<Link> = self.links.into_values().flatten().collect();
        if sort {
            links.sort_by_key(|link| link.created_at);
        }
        for link in links.into_iter() {
            let source_index = node_indices.get(&link.source_coto_id).unwrap();
            let target_index = node_indices.get(&link.target_coto_id).unwrap();
            petgraph.add_edge(*source_index, *target_index, link);
        }
        petgraph
    }
}
