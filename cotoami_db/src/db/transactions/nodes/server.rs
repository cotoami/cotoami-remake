use anyhow::Result;

use crate::{
    db::{
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
        encryption_password: &str,
        operator: &Operator,
    ) -> Result<ServerNode> {
        operator.requires_to_be_owner()?;
        self.write_transaction(server_ops::save_server_password(
            id,
            password,
            encryption_password,
        ))
    }
}
