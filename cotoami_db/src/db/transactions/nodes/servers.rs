use anyhow::{ensure, Context as _, Result};

use crate::{
    db::{
        error::DatabaseError,
        op::*,
        ops::node_role_ops::{self, server_ops, NewDatabaseRole},
        DatabaseSession,
    },
    models::prelude::*,
};

impl<'a> DatabaseSession<'a> {
    pub fn server_node(
        &mut self,
        id: &Id<Node>,
        operator: &Operator,
    ) -> Result<Option<ServerNode>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(server_ops::get(id))
    }

    pub fn try_get_server_node(
        &mut self,
        id: &Id<Node>,
        operator: &Operator,
    ) -> Result<ServerNode> {
        operator.requires_to_be_owner()?;
        self.read_transaction(server_ops::try_get(id))?
            .map_err(anyhow::Error::from)
    }

    pub fn all_server_nodes(&mut self, operator: &Operator) -> Result<Vec<(ServerNode, Node)>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(server_ops::all_pairs())
    }

    /// Registers the specified node as a server.
    ///
    /// * The [Node] data has to be imported before registered as a [ServerNode].
    /// * A unique constraint error will be returned if the specified node has already been
    ///   registered as a server.
    pub fn register_server_node(
        &self,
        id: &Id<Node>,
        url_prefix: &str,
        database_role: NewDatabaseRole,
        operator: &Operator,
    ) -> Result<(ServerNode, DatabaseRole)> {
        operator.requires_to_be_owner()?;
        ensure!(
            *id != self.globals.try_get_local_node_id()?,
            "The local node can't be a server."
        );

        let (server, database_role) = self.write_transaction(
            node_role_ops::register_server_node(id, url_prefix, database_role),
        )?;
        if let DatabaseRole::Parent(parent) = &database_role {
            self.globals.cache_parent_node(parent.clone());
        }
        Ok((server, database_role))
    }

    pub fn register_server_node_as_parent(
        &self,
        id: &Id<Node>,
        url_prefix: &str,
        operator: &Operator,
    ) -> Result<(ServerNode, ParentNode)> {
        let (server, database_role) =
            self.register_server_node(id, url_prefix, NewDatabaseRole::Parent, operator)?;
        let DatabaseRole::Parent(parent) = database_role else { unreachable!() };
        Ok((server, parent))
    }

    pub fn save_server_password(
        &self,
        id: &Id<Node>,
        password: &str,
        owner_password: &str,
        operator: &Operator,
    ) -> Result<ServerNode> {
        operator.requires_to_be_owner()?;

        // Verify the owner_password
        let local_node = self.globals.try_read_local_node()?;
        local_node
            .as_principal()
            .verify_password(owner_password)
            .context(DatabaseError::AuthenticationFailed)?;

        // Save the password to the server node
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let mut update_server = UpdateServerNode::new(id);
            update_server.set_password(password, owner_password)?;
            let server = server_ops::update(&update_server).run(ctx)?;
            Ok(server)
        })
    }
}
