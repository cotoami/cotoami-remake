use axum::{extract::State, middleware, routing::get, Extension, Json, Router};
use cotoami_db::prelude::*;
use tower::ServiceBuilder;

use crate::{service::ServiceError, state::NodeState};

mod children;
mod clients;
mod parents;
mod servers;

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/local", get(local_node))
        .nest("/servers", servers::routes())
        .nest("/clients", clients::routes())
        .nest("/parents", parents::routes())
        .nest("/children", children::routes())
        .layer(
            ServiceBuilder::new()
                .layer(middleware::from_fn(super::require_operator))
                .layer(middleware::from_fn(super::require_session)),
        )
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/local
/////////////////////////////////////////////////////////////////////////////

async fn local_node(
    State(state): State<NodeState>,
    Extension(_operator): Extension<Operator>,
) -> Result<Json<Node>, ServiceError> {
    state
        .local_node()
        .await
        .map(Json)
        .map_err(ServiceError::from)
}
