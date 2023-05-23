//! Graph-related data structure and operations

use super::coto::{Coto, Cotonoma, Link};
use super::Id;
use std::collections::HashMap;

/// A graph is a set of cotos that are connected with links
#[derive(Debug, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct Graph {
    /// Root cotonoma
    cotonoma: Cotonoma,

    /// All the cotos in this graph, each of which is mapped by its ID
    cotos: HashMap<Id<Coto>, Coto>,

    /// All the links in this graph, each of which is mapped by the ID of the tail coto
    links: HashMap<Id<Coto>, Vec<Link>>,
}

impl Graph {
    pub fn new(cotonoma: Cotonoma) -> Self {
        Graph {
            cotonoma,
            cotos: HashMap::default(),
            links: HashMap::default(),
        }
    }

    pub fn cotonoma(&self) -> &Cotonoma {
        &self.cotonoma
    }

    pub fn add_coto(&mut self, coto: Coto) {
        self.cotos.insert(coto.uuid, coto);
    }

    pub fn contains(&self, coto_id: &Id<Coto>) -> bool {
        self.cotos.contains_key(coto_id)
    }

    pub fn add_link(&mut self, link: Link) {
        self.links
            .entry(link.tail_coto_id)
            .or_insert_with(|| vec![])
            .push(link);
    }
}
