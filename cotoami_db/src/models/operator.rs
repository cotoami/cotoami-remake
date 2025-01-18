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
    LocalNode(Id<Node>),
    ChildNode(ChildNode),
}

impl Operator {
    pub fn node_id(&self) -> Id<Node> {
        match self {
            Operator::LocalNode(node_id) => *node_id,
            Operator::ChildNode(child_node) => child_node.node_id,
        }
    }

    pub fn has_owner_permission(&self) -> bool {
        match self {
            Operator::LocalNode(_) => true,
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
        if self.can_update_coto(coto).is_ok() || self.has_owner_permission() {
            Ok(())
        } else {
            Err(DatabaseError::PermissionDenied)
        }
    }

    pub fn can_rename_cotonoma(&self, cotonoma_coto: &Coto) -> Result<(), DatabaseError> {
        if self.can_update_coto(cotonoma_coto).is_ok() || self.has_owner_permission() {
            Ok(())
        } else {
            Err(DatabaseError::PermissionDenied)
        }
    }

    pub fn can_edit_links(&self) -> Result<(), DatabaseError> {
        if self.has_owner_permission() {
            return Ok(());
        }

        if let Operator::ChildNode(ChildNode {
            can_edit_links: true,
            ..
        }) = self
        {
            return Ok(());
        }

        Err(DatabaseError::PermissionDenied)
    }
}
