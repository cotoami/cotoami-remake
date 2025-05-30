use std::sync::Arc;

use anyhow::Result;
use axum::{
    Router,
    extract::{Extension, State},
    middleware,
    routing::get,
};
use axum_extra::TypedHeader;
use cotoami_db::prelude::*;

use crate::{
    service::{ServiceError, models::InitialDataset},
    state::NodeState,
    web::{Accept, Content},
};

mod changes;
mod cotonomas;
mod cotos;
mod itos;
mod nodes;

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(initial_dataset))
        .nest("/changes", changes::routes())
        .nest("/nodes", nodes::routes())
        .nest("/cotos", cotos::routes())
        .nest("/cotonomas", cotonomas::routes())
        .nest("/itos", itos::routes())
        .layer(middleware::from_fn(super::require_operator))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data
/////////////////////////////////////////////////////////////////////////////

#[axum_macros::debug_handler]
async fn initial_dataset(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
) -> Result<Content<InitialDataset>, ServiceError> {
    state
        .initial_dataset(Arc::new(operator))
        .await
        .map(|dataset| Content(dataset, accept))
}
