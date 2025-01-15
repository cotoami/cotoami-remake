use std::sync::Arc;

use anyhow::Result;
use axum::{
    extract::{Json, State},
    routing::post,
    Extension, Router, TypedHeader,
};
use cotoami_db::prelude::*;

use crate::{
    service::ServiceError,
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> { Router::new().route("/", post(connect)) }

/////////////////////////////////////////////////////////////////////////////
// POST /api/data/links
/////////////////////////////////////////////////////////////////////////////

async fn connect(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Json(input): Json<LinkInput<'static>>,
) -> Result<Content<Link>, ServiceError> {
    state
        .connect(input, Arc::new(operator))
        .await
        .map(|link| Content(link, accept))
}
