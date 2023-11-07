use std::sync::Arc;

use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;

use super::error::ApiError;

pub(crate) async fn get_local_node(db: Arc<Database>) -> Result<Node, ApiError> {
    spawn_blocking(move || {
        let mut db = db.new_session()?;
        Ok(db.local_node()?)
    })
    .await?
}
