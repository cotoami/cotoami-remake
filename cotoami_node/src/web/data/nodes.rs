use axum::{
    extract::{Path, State},
    routing::get,
    Router,
};
use axum_extra::TypedHeader;
use cotoami_db::prelude::*;

use crate::{
    service::{models::NodeDetails, ServiceError},
    state::NodeState,
    web::{Accept, Content},
};

mod children;
mod clients;
mod cotonomas;
mod cotos;
mod local;
mod parents;
mod servers;

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/{node_id}/details", get(node_details))
        .nest("/{node_id}/cotonomas", cotonomas::routes())
        .nest("/{node_id}/cotos", cotos::routes())
        .nest("/local", local::routes())
        .nest("/servers", servers::routes())
        .nest("/clients", clients::routes())
        .nest("/parents", parents::routes())
        .nest("/children", children::routes())
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/:node_id/details
/////////////////////////////////////////////////////////////////////////////

async fn node_details(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(node_id): Path<Id<Node>>,
) -> Result<Content<NodeDetails>, ServiceError> {
    state
        .node_details(node_id)
        .await
        .map(|details| Content(details, accept))
}
