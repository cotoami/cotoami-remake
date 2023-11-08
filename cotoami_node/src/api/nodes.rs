use std::sync::Arc;

use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use super::error::ApiError;

pub(crate) async fn local_node(db: Arc<Database>) -> Result<Node, ApiError> {
    spawn_blocking(move || Ok(db.new_session()?.local_node()?)).await?
}
