use anyhow::Result;
use cotoami_db::prelude::*;

use crate::{
    service::{
        models::{Changes, ChunkOfChanges},
        ServiceError,
    },
    state::NodeState,
};

impl NodeState {
    pub(crate) async fn chunk_of_changes(&self, from: i64) -> Result<ChunkOfChanges, ServiceError> {
        let changes_chunk_size = self.config().changes_chunk_size;
        self.get(
            move |ds| match ds.chunk_of_changes(from, changes_chunk_size) {
                Ok((chunk, last_serial_number)) => Ok(ChunkOfChanges::Fetched(Changes {
                    chunk,
                    last_serial_number,
                })),
                Err(anyhow_err) => {
                    if let Some(DatabaseError::ChangeNumberOutOfRange { max, .. }) =
                        anyhow_err.downcast_ref::<DatabaseError>()
                    {
                        Ok(ChunkOfChanges::OutOfRange { max: *max })
                    } else {
                        Err(anyhow_err.into())
                    }
                }
            },
        )
        .await
    }
}
