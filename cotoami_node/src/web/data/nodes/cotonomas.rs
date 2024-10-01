use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    routing::get,
    Router, TypedHeader,
};
use cotoami_db::prelude::*;
use validator::Validate;

use crate::{
    service::{error::IntoServiceResult, models::Pagination, ServiceError},
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotonomas))
        .route("/:name", get(get_cotonoma_by_name))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/:node_id/cotonomas
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotonomas(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(node_id): Path<Id<Node>>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<Page<Cotonoma>>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return errors.into_result();
    }
    state
        .recent_cotonomas(Some(node_id), pagination)
        .await
        .map(|cotonomas| Content(cotonomas, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/:node_id/cotonomas/:name
/////////////////////////////////////////////////////////////////////////////

async fn get_cotonoma_by_name(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path((node_id, name)): Path<(Id<Node>, String)>,
) -> Result<Content<Cotonoma>, ServiceError> {
    state
        .cotonoma_by_name(name, node_id)
        .await
        .map(|cotonoma| Content(cotonoma, accept))
}
