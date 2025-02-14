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
    Anonymous,
}

impl Operator {
    pub fn node_id(&self) -> Option<Id<Node>> {
        match self {
            Operator::LocalNode(node_id) => Some(*node_id),
            Operator::ChildNode(child_node) => Some(child_node.node_id),
            Operator::Anonymous => None,
        }
    }

    pub fn try_get_node_id(&self) -> Result<Id<Node>, DatabaseError> {
        self.node_id().ok_or(DatabaseError::PermissionDenied)
    }

    pub fn has_owner_permission(&self) -> bool {
        match self {
            Operator::LocalNode(_) => true,
            Operator::ChildNode(child_node) => child_node.as_owner,
            Operator::Anonymous => false,
        }
    }

    pub fn requires_to_be_owner(&self) -> Result<(), DatabaseError> {
        if self.has_owner_permission() {
            Ok(())
        } else {
            Err(DatabaseError::PermissionDenied)
        }
    }

    pub fn can_post(&self) -> Result<(), DatabaseError> {
        if self.node_id().is_some() {
            Ok(())
        } else {
            Err(DatabaseError::PermissionDenied)
        }
    }

    pub fn can_update_coto(&self, coto: &Coto) -> Result<(), DatabaseError> {
        if self.node_id() == Some(coto.posted_by_id) {
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
