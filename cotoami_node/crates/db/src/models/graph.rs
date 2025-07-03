//! Graph-related data structure and operations

use std::collections::HashMap;

use petgraph::prelude::{Graph as Petgraph, NodeIndex};

use super::{coto::Coto, ito::Ito, Id};

/// A graph is a set of cotos that are connected with itos
#[derive(Debug, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct Graph {
    /// Root coto ID
    pub root_id: Id<Coto>,

    /// All the cotos in this graph, each of which is mapped by its ID
    pub cotos: HashMap<Id<Coto>, Coto>,

    /// All the itos in this graph, each of which is mapped by the ID of the source coto
    pub itos: HashMap<Id<Coto>, Vec<Ito>>,
}

impl Graph {
    /// Creates an empty graph with a root cotonoma
    pub fn new(root: Coto) -> Self {
        let mut graph = Self {
            root_id: root.uuid,
            cotos: HashMap::new(),
            itos: HashMap::new(),
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

    pub fn count_cotos(&self) -> usize { self.cotos.len() }

    pub fn add_ito(&mut self, ito: Ito) {
        self.itos.entry(ito.source_coto_id).or_default().push(ito);
    }

    pub fn count_itos(&self) -> usize {
        self.itos.values().fold(0, |count, itos| count + itos.len())
    }

    pub(crate) fn sort_itos(&mut self) {
        for itos in self.itos.values_mut() {
            itos.sort_by_key(|ito| ito.order);
        }
    }

    pub fn assert_itos_sorted(&self) {
        for itos in self.itos.values() {
            let order: Vec<usize> = itos.iter().map(|ito| ito.order as usize).collect();
            let expected: Vec<usize> = (1..=itos.len()).collect();
            assert_eq!(order, expected);
        }
    }

    /// Converts this graph into a petgraph's [petgraph::graph::Graph].
    ///
    /// You can use the `sort` flag to get cotos and itos in a predictable order.
    pub fn into_petgraph(self, sort: bool) -> Petgraph<Coto, Ito> {
        let mut petgraph = Petgraph::<Coto, Ito>::new();

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

        // Itos
        let mut itos: Vec<Ito> = self.itos.into_values().flatten().collect();
        if sort {
            itos.sort_by_key(|ito| ito.created_at);
        }
        for ito in itos.into_iter() {
            let source_index = node_indices.get(&ito.source_coto_id).unwrap();
            let target_index = node_indices.get(&ito.target_coto_id).unwrap();
            petgraph.add_edge(*source_index, *target_index, ito);
        }
        petgraph
    }
}
