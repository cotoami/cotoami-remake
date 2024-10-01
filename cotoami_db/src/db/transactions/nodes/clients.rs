use core::time::Duration;

use anyhow::Result;

use crate::{
    db::{
        op::*,
        ops::{
            node_ops,
            node_role_ops::{self, client_ops, NewDatabaseRole},
            Page,
        },
        DatabaseSession,
    },
    models::prelude::*,
};

impl<'a> DatabaseSession<'a> {
    pub fn all_client_nodes(&mut self, operator: &Operator) -> Result<Vec<(ClientNode, Node)>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(client_ops::all_pairs())
    }

    pub fn recent_client_nodes(
        &mut self,
        page_size: i64,
        page_index: i64,
        operator: &Operator,
    ) -> Result<Page<(ClientNode, Node)>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(client_ops::recent_pairs(page_size, page_index))
    }

    /// Registers the specified node as a client.
    ///
    /// This operation is assumed to be invoked by a node owner to allow another node
    /// to connect to this node.
    ///
    /// If the node specified by the ID doesn't exist in this database (normally it doesn't),
    /// this function will create a placeholder row in the `nodes` table. The row will be
    /// updated with real data when the client node connects to this node.
    pub fn register_client_node(
        &self,
        id: Id<Node>,
        password: &str,
        database_role: NewDatabaseRole,
        operator: &Operator,
    ) -> Result<(ClientNode, Node, DatabaseRole)> {
        operator.requires_to_be_owner()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let node = node_ops::get_or_insert_placeholder(id).run(ctx)?;
            let (client, database_role) =
                node_role_ops::register_client_node(&node.uuid, password, database_role)
                    .run(ctx)?;
            if let DatabaseRole::Parent(parent) = &database_role {
                self.globals.cache_parent_node(parent.clone());
            }
            Ok((client, node, database_role))
        })
    }

    pub fn start_client_node_session(
        &self,
        id: &Id<Node>,
        password: &str,
        duration: Duration,
    ) -> Result<ClientNode> {
        self.write_transaction(client_ops::start_session(id, password, duration))
    }

    pub fn clear_client_node_session(&self, id: &Id<Node>) -> Result<ClientNode> {
        self.write_transaction(client_ops::clear_session(id))
    }

    pub fn change_client_node_password(&self, id: &Id<Node>, password: &str) -> Result<ClientNode> {
        self.write_transaction(client_ops::change_password(id, password))
    }

    pub fn client_session(&mut self, token: &str) -> Result<Option<ClientSession>> {
        // a client node?
        if let Some(client) = self.read_transaction(client_ops::get_by_session_token(token))? {
            if client.as_principal().verify_session(token).is_ok() {
                match self.database_role_of(&client.node_id)? {
                    Some(DatabaseRole::Parent(parent)) => {
                        return Ok(Some(ClientSession::ParentNode(parent)));
                    }
                    Some(DatabaseRole::Child(child)) => {
                        return Ok(Some(ClientSession::Operator(Operator::ChildNode(child))));
                    }
                    None => (),
                }
            }
        }

        // the owner of local node?
        let local_node = self.globals.try_read_local_node()?;
        if local_node.as_principal().verify_session(token).is_ok() {
            return Ok(Some(ClientSession::Operator(Operator::Owner(
                local_node.node_id,
            ))));
        }

        Ok(None) // no session
    }
}
