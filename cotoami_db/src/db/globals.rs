//! Global information shared among sessions in a database.
//! Most of the fields are cached database rows or column values frequently used internally.
//! For example, [LocalNode] will be used every time when authentication is needed.

use std::collections::HashMap;

use anyhow::{anyhow, ensure, Result};
use diesel::sqlite::SqliteConnection;
use parking_lot::{
    MappedRwLockReadGuard, MappedRwLockWriteGuard, RwLock, RwLockReadGuard, RwLockWriteGuard,
};

use crate::{
    db::{
        error::*,
        op,
        ops::node_role_ops::{local_ops, parent_ops},
    },
    models::prelude::*,
};

#[derive(Debug, Default)]
pub struct Globals {
    local_node: RwLock<Option<LocalNode>>,
    root_cotonoma_id: RwLock<Option<Id<Cotonoma>>>,
    parent_nodes: RwLock<HashMap<Id<Node>, ParentNode>>,
}

impl Globals {
    pub(super) fn init(&mut self, conn: &mut SqliteConnection) -> Result<()> {
        // local_node, root_cotonoma_id
        if let Some((local_node, node)) = op::run_read(conn, local_ops::get_pair())? {
            self.set_local_node(Some(local_node));
            self.set_root_cotonoma_id(node.root_cotonoma_id);
        } else {
            self.set_local_node(None);
            self.set_root_cotonoma_id(None);
        }

        // parent_nodes
        self.replace_parent_nodes(op::run_read(conn, parent_ops::all())?);

        Ok(())
    }

    /////////////////////////////////////////////////////////////////////////////
    // local_node
    /////////////////////////////////////////////////////////////////////////////

    pub fn has_local_node(&self) -> bool { self.local_node.read().is_some() }

    pub fn try_get_local_node_id(&self) -> Result<Id<Node>> {
        Ok(self.try_read_local_node()?.node_id)
    }

    pub fn local_node(&self) -> Option<LocalNode> { self.local_node.read().clone() }

    pub fn try_get_local_node(&self) -> Result<LocalNode> {
        Ok(self.try_read_local_node()?.clone())
    }

    pub fn is_local_node(&self, id: &Id<Node>) -> bool {
        match self.local_node() {
            Some(local_node) => *id == local_node.node_id,
            None => false,
        }
    }

    pub fn local_node_as_operator(&self) -> Result<Operator> {
        Ok(Operator::LocalNode(self.try_get_local_node_id()?))
    }

    pub fn ensure_local<T: BelongsToNode + std::fmt::Debug>(&self, entity: &T) -> Result<()> {
        ensure!(
            *entity.node_id() == self.try_get_local_node_id()?,
            "The entity doesn't belong to the local node: {entity:?}"
        );
        Ok(())
    }

    pub fn is_local<T: BelongsToNode + std::fmt::Debug>(&self, entity: &T) -> bool {
        self.ensure_local(entity).is_ok()
    }

    pub fn try_read_local_node(&self) -> Result<MappedRwLockReadGuard<LocalNode>> {
        RwLockReadGuard::try_map(self.local_node.read(), |x| x.as_ref())
            .map_err(|_| anyhow!(DatabaseError::LocalNodeNotYetInitialized))
    }

    pub(crate) fn set_local_node(&self, local_node: Option<LocalNode>) {
        *self.local_node.write() = local_node;
    }

    pub(crate) fn try_write_local_node(&self) -> Result<MappedRwLockWriteGuard<LocalNode>> {
        RwLockWriteGuard::try_map(self.local_node.write(), |x| x.as_mut())
            .map_err(|_| anyhow!(DatabaseError::LocalNodeNotYetInitialized))
    }

    /////////////////////////////////////////////////////////////////////////////
    // root_cotonoma_id
    /////////////////////////////////////////////////////////////////////////////

    pub(crate) fn set_root_cotonoma_id(&self, root_cotonoma_id: Option<Id<Cotonoma>>) {
        *self.root_cotonoma_id.write() = root_cotonoma_id;
    }

    pub fn root_cotonoma_id(&self) -> Option<Id<Cotonoma>> { *self.root_cotonoma_id.read() }

    pub fn is_root_cotonoma(&self, id: &Id<Cotonoma>) -> bool {
        Some(*id) == self.root_cotonoma_id()
    }

    /////////////////////////////////////////////////////////////////////////////
    // parent_nodes
    /////////////////////////////////////////////////////////////////////////////

    pub fn is_parent(&self, id: &Id<Node>) -> bool { self.parent_nodes.read().contains_key(id) }

    pub fn parent_node(&self, id: &Id<Node>) -> Option<ParentNode> {
        self.parent_nodes.read().get(id).cloned()
    }

    pub(crate) fn replace_parent_nodes(&self, parent_nodes: Vec<ParentNode>) {
        let mut map = self.parent_nodes.write();
        map.clear();
        for parent in parent_nodes {
            map.insert(parent.node_id, parent);
        }
    }

    pub(crate) fn cache_parent_node(&self, parent: ParentNode) {
        self.parent_nodes.write().insert(parent.node_id, parent);
    }

    pub(crate) fn try_write_parent_node(
        &self,
        id: &Id<Node>,
    ) -> Result<MappedRwLockWriteGuard<ParentNode>> {
        RwLockWriteGuard::try_map(self.parent_nodes.write(), |x| x.get_mut(id))
            .map_err(|_| anyhow!(DatabaseError::not_found(EntityKind::ParentNode, *id)))
    }
}
