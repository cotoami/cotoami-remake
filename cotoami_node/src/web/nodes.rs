use axum::{
    extract::{Path, Query, State},
    middleware,
    routing::get,
    Extension, Router, TypedHeader,
};
use cotoami_db::prelude::*;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{PaginatedCotos, Pagination},
        ServiceError,
    },
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
        .route("/:node_id/cotos", get(recent_cotos))
        .route("/:node_id/cotos/search/:query", get(search_cotos))
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

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/:node_id/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(node_id): Path<Id<Node>>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("cotos", errors).into_result();
    }
    state
        .recent_cotos(Some(node_id), None, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/:node_id/cotos/search/:query
/////////////////////////////////////////////////////////////////////////////

async fn search_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path((node_id, query)): Path<(Id<Node>, String)>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("cotos", errors).into_result();
    }
    state
        .search_cotos(query, Some(node_id), None, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}
