use core::time::Duration;
use std::ops::DerefMut;

use anyhow::{bail, Result};

use crate::{
    db::{
        error::*,
        op::*,
        ops::{
            changelog_ops, node_ops,
            node_role_ops::{local_ops, server_ops},
        },
        DatabaseSession,
    },
    models::prelude::*,
};

impl<'a> DatabaseSession<'a> {
    /// Creates initial data that represents a local node and its root cotonoma.
    ///
    /// Majority of the database operations require this operation to be called in advance
    /// because every database entity belongs to a node.
    ///
    /// A root cotonoma will be created only if some `name` is specified,
    /// otherwise the local node will be initialized as cotonoma-less and its name
    /// will be set to empty string.
    pub fn init_as_node(
        &self,
        name: Option<&str>,
        password: Option<&str>,
    ) -> Result<((LocalNode, Node), ChangelogEntry)> {
        let result = self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let (local_node, mut node) =
                local_ops::create(name.unwrap_or_default(), password).run(ctx)?;

            // Create a root cotonoma if the `name` is not None
            let change = if let Some(name) = name {
                let (node_updated, cotonoma, coto) =
                    node_ops::create_root_cotonoma(&node.uuid, name).run(ctx)?;
                node = node_updated;
                Change::CreateNode {
                    node,
                    root: Some((cotonoma, coto)),
                }
            } else {
                Change::CreateNode { node, root: None }
            };

            let changelog = changelog_ops::log_change(&change, &local_node.node_id).run(ctx)?;

            // Take the node data back from the `change` struct
            let Change::CreateNode { node, root: _ } = change else { unreachable!() };

            Ok(((local_node, node), changelog))
        });

        // Put the local node data in the global cache
        if let Ok(((local_node, node), _)) = &result {
            self.globals.set_local_node(Some(local_node.clone()));
            self.globals.set_root_cotonoma_id(node.root_cotonoma_id);
        }

        result
    }

    pub fn local_node(&mut self) -> Result<Node> {
        if let Some((_, node)) = self.read_transaction(local_ops::get_pair())? {
            Ok(node)
        } else {
            bail!(DatabaseError::LocalNodeNotYetInitialized)
        }
    }

    pub fn local_node_pair(&mut self, operator: &Operator) -> Result<(LocalNode, Node)> {
        operator.requires_to_be_owner()?;
        let pair = self
            .read_transaction(local_ops::get_pair())?
            // Any operator doesn't exist without the local node initialized
            .unwrap_or_else(|| unreachable!());
        Ok(pair)
    }

    pub fn rename_local_node(
        &self,
        name: &str,
        operator: &Operator,
    ) -> Result<(Node, ChangelogEntry)> {
        operator.requires_to_be_owner()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let updated_at = crate::current_datetime();
            let node = node_ops::rename(&local_node_id, name, Some(updated_at)).run(ctx)?;
            let change = Change::RenameNode {
                node_id: local_node_id,
                name: name.into(),
                updated_at,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((node, changelog))
        })
    }

    pub fn set_local_node_icon(
        &self,
        icon: &bytes::Bytes,
        operator: &Operator,
    ) -> Result<(Node, ChangelogEntry)> {
        operator.requires_to_be_owner()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let node = node_ops::set_icon(&local_node_id, icon).run(ctx)?;
            let change = Change::SetNodeIcon {
                node_id: local_node_id,
                icon: node.icon.clone(),
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((node, changelog))
        })
    }

    pub fn set_root_cotonoma(
        &self,
        cotonoma_id: &Id<Cotonoma>,
        operator: &Operator,
    ) -> Result<(Node, ChangelogEntry)> {
        operator.requires_to_be_owner()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let node = node_ops::set_root_cotonoma(&local_node_id, cotonoma_id).run(ctx)?;
            let change = Change::SetRootCotonoma {
                node_id: local_node_id,
                cotonoma_id: *cotonoma_id,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((node, changelog))
        })
    }

    pub fn replicate(&mut self, parent_node: &Node) -> Result<Option<ChangelogEntry>> {
        if let Some(parent_cotonoma_id) = parent_node.root_cotonoma_id {
            let (_, change) = self
                .set_root_cotonoma(&parent_cotonoma_id, &self.globals.local_node_as_operator()?)?;
            Ok(Some(change))
        } else {
            Ok(None)
        }
    }

    /////////////////////////////////////////////////////////////////////////////
    // node owner
    /////////////////////////////////////////////////////////////////////////////

    pub fn start_owner_session(&self, password: &str, duration: Duration) -> Result<LocalNode> {
        let mut local_node = self.globals.try_write_local_node()?;
        self.write_transaction(local_ops::start_session(
            local_node.deref_mut(),
            password,
            duration,
        ))?;
        Ok(local_node.clone())
    }

    pub fn clear_owner_session(&self) -> Result<()> {
        let mut local_node = self.globals.try_write_local_node()?;
        self.write_transaction(local_ops::clear_session(local_node.deref_mut()))
    }

    pub fn set_owner_password_if_none(&self, new_password: &str) -> Result<()> {
        let mut local_node = self.globals.try_write_local_node()?;
        if local_node.password_hash().is_some() {
            bail!("The local node already has a password.");
        }
        local_node.update_password(new_password)?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            local_ops::update(&local_node).run(ctx)?;
            server_ops::clear_all_passwords().run(ctx)?;
            Ok(())
        })
    }

    pub fn change_owner_password(&self, new_password: &str, old_password: &str) -> Result<()> {
        let mut local_node = self.globals.try_write_local_node()?;
        local_node.verify_password(old_password)?;
        local_node.update_password(new_password)?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            local_ops::update(&local_node).run(ctx)?;
            server_ops::reencrypt_all_passwords(new_password, old_password).run(ctx)
        })
    }
}
