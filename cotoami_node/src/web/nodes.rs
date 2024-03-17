use axum::{extract::State, middleware, routing::get, Extension, Router, TypedHeader};
use cotoami_db::prelude::*;

use crate::{
    service::ServiceError,
    state::NodeState,
    web::{Accept, Content},
};

mod children;
mod clients;
mod cotonomas;
mod parents;
mod servers;

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/local", get(local_node))
        .nest("/servers", servers::routes())
        .nest("/clients", clients::routes())
        .nest("/parents", parents::routes())
        .nest("/children", children::routes())
        .nest("/:node_id/cotonomas", cotonomas::routes())
        .layer(middleware::from_fn(super::require_operator))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/local
/////////////////////////////////////////////////////////////////////////////

async fn local_node(
    State(state): State<NodeState>,
    Extension(_operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
) -> Result<Content<Node>, ServiceError> {
    state.local_node().await.map(|x| Content(x, accept))
}
