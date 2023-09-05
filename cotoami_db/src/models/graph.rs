//! Graph-related data structure and operations

use std::collections::HashMap;

use petgraph::prelude::{Graph as Petgraph, NodeIndex};

use super::{coto::Coto, cotonoma::Cotonoma, link::Link, Id};

/// A graph is a set of cotos that are connected with links
#[derive(Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct Graph {
    /// Root cotonoma
    root_cotonoma: Cotonoma,

    /// All the cotos in this graph, each of which is mapped by its ID
    cotos: HashMap<Id<Coto>, Coto>,

    /// All the links in this graph, each of which is mapped by the ID of the source coto
    links: HashMap<Id<Coto>, Vec<Link>>,
}

impl Graph {
    /// Creates a graph with a root cotonoma
    pub fn new(root_cotonoma: (Cotonoma, Coto)) -> Self {
        let (cotonoma, cotonoma_coto) = root_cotonoma;
        assert_eq!(cotonoma.coto_id, cotonoma_coto.uuid);

        let mut graph = Graph {
            root_cotonoma: cotonoma,
            cotos: HashMap::default(),
            links: HashMap::default(),
        };
        graph.add_coto(cotonoma_coto);
        graph
    }

    pub fn root_cotonoma(&self) -> &Cotonoma { &self.root_cotonoma }

    pub fn add_coto(&mut self, coto: Coto) { self.cotos.insert(coto.uuid, coto); }

    pub fn contains(&self, coto_id: &Id<Coto>) -> bool { self.cotos.contains_key(coto_id) }

    pub fn add_link(&mut self, link: Link) {
        self.links
            .entry(link.source_coto_id)
            .or_insert_with(Vec::new)
            .push(link);
    }

    /// Converts it into a petgraph mainly for debug purposes
    pub fn into_petgraph(&self) -> Petgraph<String, &str> {
        let mut petgraph = Petgraph::<String, &str>::new();

        // cotos
        let mut cotos: Vec<&Coto> = self.cotos.values().collect();
        cotos.sort_by_key(|coto| coto.created_at);
        let mut node_indexes: HashMap<Id<Coto>, NodeIndex> = HashMap::new();
        for coto in cotos.iter() {
            let node_index = petgraph.add_node(coto.to_string());
            node_indexes.insert(coto.uuid, node_index);
        }

        // edges
        let mut links: Vec<&Link> = self.links.values().flatten().collect();
        links.sort_by_key(|link| link.created_at);
        for link in links.iter() {
            let source_index = node_indexes.get(&link.source_coto_id).unwrap();
            let target_index = node_indexes.get(&link.target_coto_id).unwrap();
            petgraph.add_edge(
                *source_index,
                *target_index,
                link.linking_phrase.as_deref().unwrap_or_default(),
            );
        }
        petgraph
    }
}
