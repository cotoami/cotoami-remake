use crate::models::node::{
    child::ChildNode, client::ClientNode, parent::ParentNode, server::ServerNode,
};

#[derive(Debug, derive_more::Display)]
pub enum NetworkRole {
    #[display("Server ({})", _0.node_id)]
    Server(ServerNode),

    #[display("Client ({})", _0.node_id)]
    Client(ClientNode),
}

#[derive(Debug, derive_more::Display)]
pub enum DatabaseRole {
    #[display("Parent ({})", _0.node_id)]
    Parent(ParentNode),

    #[display("Child ({})", _0.node_id)]
    Child(ChildNode),
}
