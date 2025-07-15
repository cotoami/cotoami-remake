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
    Agent {
        node_id: Id<Node>,
        can_edit_user_content: bool,
    },
    Anonymous,
}

impl Operator {
    pub fn node_id(&self) -> Option<Id<Node>> {
        match self {
            Operator::LocalNode(node_id) => Some(*node_id),
            Operator::ChildNode(child_node) => Some(child_node.node_id),
            Operator::Agent { node_id, .. } => Some(*node_id),
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
            Operator::Agent { .. } => false,
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

    pub fn can_post_cotos(&self) -> Result<(), DatabaseError> {
        if self.node_id().is_some() {
            Ok(())
        } else {
            Err(DatabaseError::PermissionDenied)
        }
    }

    pub fn can_post_cotonomas(&self) -> Result<(), DatabaseError> {
        if self.has_owner_permission() {
            return Ok(());
        }
        match self {
            Operator::ChildNode(ChildNode {
                can_post_cotonomas: true,
                ..
            }) => Ok(()),
            Operator::Agent { .. } => Ok(()),
            _ => Err(DatabaseError::PermissionDenied),
        }
    }

    /// Checks if this operator can update the given coto.
    /// The coto must belong to the local node.
    pub fn can_update_coto(&self, coto: &Coto) -> Result<(), DatabaseError> {
        // Agent with a special privilege
        if let Operator::Agent {
            can_edit_user_content: true,
            ..
        } = self
        {
            return Ok(());
        }

        // Basically only the poster can update a coto,
        if self.node_id() == Some(coto.posted_by_id)
            // but if a coto is a cotonoma, owners can update it, too.
            || (coto.is_cotonoma && self.has_owner_permission())
        {
            Ok(())
        } else {
            Err(DatabaseError::PermissionDenied)
        }
    }

    /// Checks if this operator can delete the given local coto.
    /// The coto must belong to the local node.
    pub fn can_delete_coto(&self, coto: &Coto) -> Result<(), DatabaseError> {
        // Agent with a special privilege
        if let Operator::Agent {
            can_edit_user_content: true,
            ..
        } = self
        {
            return Ok(());
        }

        if self.can_update_coto(coto).is_ok() || self.has_owner_permission() {
            Ok(())
        } else {
            Err(DatabaseError::PermissionDenied)
        }
    }

    pub fn can_edit_itos(&self) -> Result<(), DatabaseError> {
        if self.has_owner_permission() {
            return Ok(());
        }
        match self {
            Operator::ChildNode(ChildNode {
                can_edit_itos: true,
                ..
            }) => Ok(()),
            Operator::Agent { .. } => Ok(()),
            _ => Err(DatabaseError::PermissionDenied),
        }
    }
}
