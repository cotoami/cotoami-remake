use axum::{
    extract::{Path, Query, State},
    routing::get,
    Router, TypedHeader,
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

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotos))
        .route("/search/:query", get(search_cotos))
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
