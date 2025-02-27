use anyhow::Result;
use async_trait::async_trait;
use cotoami_db::prelude::*;

use crate::service::{
    Command, NodeService, RemoteNodeService,
    models::{ChunkOfChanges, ClientNodeSession, CreateClientNodeSession, InitialDataset},
};

/// An extension trait for [NodeService] that provides shortcut functions for
/// frequently used commands.
///
/// `async fn` is now supported in Rust 1.75.0:
/// https://blog.rust-lang.org/2023/12/21/async-fn-rpit-in-traits.html
/// However, it is still discouraged for general use in public traits and
/// APIs for the reason that users can't put additional bounds on the return type.
///
/// We want this extension trait to be public so that other crate can use it,
/// so decided to use the `async_trait` crate until this limitation is removed.
/// https://crates.io/crates/async-trait
#[async_trait]
pub trait NodeServiceExt: NodeService {
    async fn initial_dataset(&self) -> Result<InitialDataset> {
        let request = Command::InitialDataset.into_request();
        let response = self.call(request).await?;
        response.content::<InitialDataset>()
    }

    async fn chunk_of_changes(&self, from: i64) -> Result<ChunkOfChanges> {
        let request = Command::ChunkOfChanges { from }.into_request();
        let response = self.call(request).await?;
        response.content::<ChunkOfChanges>()
    }

    async fn post_coto(&self, input: CotoInput<'static>, post_to: Id<Cotonoma>) -> Result<Coto> {
        let request = Command::PostCoto { input, post_to }.into_request();
        let response = self.call(request).await?;
        response.content::<Coto>()
    }

    async fn post_cotonoma(
        &self,
        input: CotonomaInput<'static>,
        post_to: Id<Cotonoma>,
    ) -> Result<(Cotonoma, Coto)> {
        let request = Command::PostCotonoma { input, post_to }.into_request();
        let response = self.call(request).await?;
        response.content::<(Cotonoma, Coto)>()
    }

    async fn edit_coto(&self, id: Id<Coto>, diff: CotoContentDiff<'static>) -> Result<Coto> {
        let request = Command::EditCoto { id, diff }.into_request();
        let response = self.call(request).await?;
        response.content::<Coto>()
    }

    async fn delete_coto(&self, id: Id<Coto>) -> Result<Id<Coto>> {
        let request = Command::DeleteCoto { id }.into_request();
        let response = self.call(request).await?;
        response.content::<Id<Coto>>()
    }

    async fn repost(&self, id: Id<Coto>, dest: Id<Cotonoma>) -> Result<(Coto, Coto)> {
        let request = Command::Repost { id, dest }.into_request();
        let response = self.call(request).await?;
        response.content::<(Coto, Coto)>()
    }

    async fn rename_cotonoma(&self, id: Id<Cotonoma>, name: String) -> Result<(Cotonoma, Coto)> {
        let request = Command::RenameCotonoma { id, name }.into_request();
        let response = self.call(request).await?;
        response.content::<(Cotonoma, Coto)>()
    }

    async fn connect(&self, input: ItoInput<'static>) -> Result<Ito> {
        let request = Command::Connect(input).into_request();
        let response = self.call(request).await?;
        response.content::<Ito>()
    }

    async fn edit_ito(&self, id: Id<Ito>, diff: ItoContentDiff<'static>) -> Result<Ito> {
        let request = Command::EditIto { id, diff }.into_request();
        let response = self.call(request).await?;
        response.content::<Ito>()
    }

    async fn disconnect(&self, id: Id<Ito>) -> Result<Id<Ito>> {
        let request = Command::Disconnect { id }.into_request();
        let response = self.call(request).await?;
        response.content::<Id<Ito>>()
    }

    async fn change_ito_order(&self, id: Id<Ito>, new_order: i32) -> Result<Ito> {
        let request = Command::ChangeItoOrder { id, new_order }.into_request();
        let response = self.call(request).await?;
        response.content::<Ito>()
    }
}

impl<T> NodeServiceExt for T where T: NodeService + ?Sized {}

#[async_trait]
pub trait RemoteNodeServiceExt: RemoteNodeService {
    async fn create_client_node_session(
        &mut self,
        input: CreateClientNodeSession,
    ) -> Result<ClientNodeSession> {
        let request = Command::CreateClientNodeSession(input).into_request();
        let response = self.call(request).await?;
        let client_node_session = response.content::<ClientNodeSession>()?;
        if let Some(ref token) = client_node_session.token {
            self.set_session_token(&token.token)?;
        }
        Ok(client_node_session)
    }
}

impl<T> RemoteNodeServiceExt for T where T: RemoteNodeService + ?Sized {}
