use anyhow::Result;

use crate::{
    db::error::*,
    models::{
        coto::Coto,
        node::{child::ChildNode, Node},
        Id,
    },
};

#[derive(Debug, Clone)]
pub enum Operator {
    Owner(Id<Node>),
    ChildNode(ChildNode),
}

impl Operator {
    pub fn node_id(&self) -> Id<Node> {
        match self {
            Operator::Owner(node_id) => *node_id,
            Operator::ChildNode(child_node) => child_node.node_id,
        }
    }

    pub fn has_owner_permission(&self) -> bool {
        match self {
            Operator::Owner(_) => true,
            Operator::ChildNode(child_node) => child_node.as_owner,
        }
    }

    pub fn requires_to_be_owner(&self) -> Result<(), DatabaseError> {
        if self.has_owner_permission() {
            Ok(())
        } else {
            Err(DatabaseError::PermissionDenied)
        }
    }

    pub fn can_update_coto(&self, coto: &Coto) -> Result<(), DatabaseError> {
        if self.node_id() == coto.posted_by_id {
            Ok(())
        } else {
            Err(DatabaseError::PermissionDenied)
        }
    }

    pub fn can_delete_coto(&self, coto: &Coto) -> Result<(), DatabaseError> {
        if self.node_id() == coto.posted_by_id || self.has_owner_permission() {
            Ok(())
        } else {
            Err(DatabaseError::PermissionDenied)
        }
    }
}
