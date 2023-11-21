use anyhow::Result;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use crate::state::AppState;

impl AppState {
    pub async fn local_node(&self) -> Result<Node> {
        let db = self.db().clone();
        spawn_blocking(move || Ok(db.new_session()?.local_node()?)).await?
    }
}
