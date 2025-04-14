//! Node role related operations

use std::collections::HashMap;

use anyhow::{bail, ensure};

use crate::{
    db::{error::*, op::*},
    models::{
        node::{
            child::{ChildNodeInput, NewChildNode},
            client::{ClientNode, NewClientNode},
            parent::{NewParentNode, ParentNode},
            roles::{DatabaseRole, NetworkRole},
            server::{NewServerNode, ServerNode},
            Node,
        },
        Id,
    },
};

pub mod child_ops;
pub mod client_ops;
pub mod local_ops;
pub mod parent_ops;
pub mod server_ops;

/////////////////////////////////////////////////////////////////////////////
// network role
/////////////////////////////////////////////////////////////////////////////

pub(crate) fn network_role_of<Conn: AsReadableConn>(
    node_id: &Id<Node>,
) -> impl Operation<Conn, Option<NetworkRole>> + '_ {
    composite_op::<Conn, _, _>(move |ctx| {
        if let Some(server) = server_ops::get(node_id).run(ctx)? {
            Ok(Some(NetworkRole::Server(server)))
        } else if let Some(client) = client_ops::get(node_id).run(ctx)? {
            Ok(Some(NetworkRole::Client(client)))
        } else {
            Ok(None)
        }
    })
}

pub enum NewNetworkRole<'a> {
    Server { url_prefix: &'a str },
    Client { password: &'a str },
}

pub(crate) fn set_network_role<'a>(
    node_id: &'a Id<Node>,
    role: NewNetworkRole<'a>,
) -> impl Operation<WritableConn, NetworkRole> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        if let Some(role) = network_role_of(node_id).run(ctx)? {
            bail!(DatabaseError::NodeRoleConflict {
                with: role.to_string()
            });
        }
        match role {
            NewNetworkRole::Server { url_prefix } => {
                let new_role = NewServerNode::new(node_id, url_prefix)?;
                let role = server_ops::insert(&new_role).run(ctx)?;
                Ok(NetworkRole::Server(role))
            }
            NewNetworkRole::Client { password } => {
                let new_role = NewClientNode::new(node_id, password)?;
                let role = client_ops::insert(&new_role).run(ctx)?;
                Ok(NetworkRole::Client(role))
            }
        }
    })
}

pub(crate) fn set_network_disabled(
    node_id: &Id<Node>,
    disabled: bool,
) -> impl Operation<WritableConn, NetworkRole> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        if !disabled {
            if let Some(DatabaseRole::Parent(parent)) = database_role_of(node_id).run(ctx)? {
                // A forked parent can't be enabled
                ensure!(
                    !parent.forked,
                    DatabaseError::AlreadyForkedFromParent {
                        parent_node_id: parent.node_id
                    }
                );
            }
        }
        match network_role_of(node_id).run(ctx)? {
            Some(NetworkRole::Server(_)) => {
                let server = server_ops::set_disabled(node_id, disabled).run(ctx)?;
                Ok(NetworkRole::Server(server))
            }
            Some(NetworkRole::Client(_)) => {
                let client = client_ops::set_disabled(node_id, disabled).run(ctx)?;
                Ok(NetworkRole::Client(client))
            }
            None => bail!(DatabaseError::not_found(EntityKind::NetworkRole, *node_id)),
        }
    })
}

/////////////////////////////////////////////////////////////////////////////
// database role
/////////////////////////////////////////////////////////////////////////////

pub(crate) fn database_role_of<Conn: AsReadableConn>(
    node_id: &Id<Node>,
) -> impl Operation<Conn, Option<DatabaseRole>> + '_ {
    composite_op::<Conn, _, _>(move |ctx| {
        if let Some(parent) = parent_ops::get(node_id).run(ctx)? {
            Ok(Some(DatabaseRole::Parent(parent)))
        } else if let Some(child) = child_ops::get(node_id).run(ctx)? {
            Ok(Some(DatabaseRole::Child(child)))
        } else {
            Ok(None)
        }
    })
}

pub(crate) fn database_roles_of<Conn: AsReadableConn>(
    node_ids: &Vec<Id<Node>>,
) -> impl Operation<Conn, HashMap<Id<Node>, DatabaseRole>> + '_ {
    composite_op::<Conn, _, _>(move |ctx| {
        let mut roles = HashMap::new();
        for parent in parent_ops::get_by_node_ids(node_ids).run(ctx)? {
            roles.insert(parent.node_id, DatabaseRole::Parent(parent));
        }
        for child in child_ops::get_by_node_ids(node_ids).run(ctx)? {
            roles.insert(child.node_id, DatabaseRole::Child(child));
        }
        Ok(roles)
    })
}

pub enum NewDatabaseRole {
    Parent,
    Child(ChildNodeInput),
}

pub(crate) fn set_database_role(
    node_id: &Id<Node>,
    role: NewDatabaseRole,
) -> impl Operation<WritableConn, DatabaseRole> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        if let Some(role) = database_role_of(node_id).run(ctx)? {
            bail!(DatabaseError::NodeRoleConflict {
                with: role.to_string()
            });
        }
        match role {
            NewDatabaseRole::Parent => {
                let new_role = NewParentNode::new(node_id)?;
                let role = parent_ops::insert(&new_role).run(ctx)?;
                Ok(DatabaseRole::Parent(role))
            }
            NewDatabaseRole::Child(input) => {
                let new_role = NewChildNode::new(node_id, &input)?;
                let role = child_ops::insert(&new_role).run(ctx)?;
                Ok(DatabaseRole::Child(role))
            }
        }
    })
}

pub(crate) fn fork_from(
    parent_node_id: &Id<Node>,
) -> impl Operation<WritableConn, ParentNode> + '_ {
    composite_op::<WritableConn, _, _>(move |ctx| {
        let parent = parent_ops::set_forked(parent_node_id).run(ctx)?;
        set_network_disabled(parent_node_id, true).run(ctx)?;
        Ok(parent)
    })
}

/////////////////////////////////////////////////////////////////////////////
// role combination
/////////////////////////////////////////////////////////////////////////////

pub(crate) fn register_server_node<'a>(
    node_id: &'a Id<Node>,
    url_prefix: &'a str,
    database_role: NewDatabaseRole,
) -> impl Operation<WritableConn, (ServerNode, DatabaseRole)> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        // Set a network role (server) to the node
        let network_role = NewNetworkRole::Server { url_prefix };
        let NetworkRole::Server(server) = set_network_role(node_id, network_role).run(ctx)? else {
            unreachable!()
        };

        // Set a database role to the node
        let database_role = set_database_role(node_id, database_role).run(ctx)?;

        Ok((server, database_role))
    })
}

pub(crate) fn register_client_node<'a>(
    node_id: &'a Id<Node>,
    password: &'a str,
    database_role: NewDatabaseRole,
) -> impl Operation<WritableConn, (ClientNode, DatabaseRole)> + 'a {
    composite_op::<WritableConn, _, _>(move |ctx| {
        // Set a network role (client) to the node
        let network_role = NewNetworkRole::Client { password };
        let NetworkRole::Client(client) = set_network_role(node_id, network_role).run(ctx)? else {
            unreachable!()
        };

        // Set a database role to the node
        let database_role = set_database_role(node_id, database_role).run(ctx)?;

        Ok((client, database_role))
    })
}
