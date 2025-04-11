use std::sync::Arc;

use axum::{
    extract::{Path, Query, State},
    routing::get,
    Extension, Form, Router,
};
use axum_extra::TypedHeader;
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{EditChild, Pagination},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_child_nodes))
        .route("/{node_id}", get(child).put(edit_child))
}

const DEFAULT_PAGE_SIZE: i64 = 30;

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/children
/////////////////////////////////////////////////////////////////////////////

async fn recent_child_nodes(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<Page<Node>>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return errors.into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db().new_session()?;
        let nodes = db
            .recent_child_nodes(
                pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
                pagination.page,
                &operator,
            )?
            .map(|(_, node)| node)
            .into();
        Ok(Content(nodes, accept))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/nodes/children/{node_id}
/////////////////////////////////////////////////////////////////////////////

async fn child(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(node_id): Path<Id<Node>>,
) -> Result<Content<ChildNode>, ServiceError> {
    state
        .child_node(node_id, Arc::new(operator))
        .await
        .map(|child| Content(child, accept))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/data/nodes/children/:node_id
/////////////////////////////////////////////////////////////////////////////

async fn edit_child(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(node_id): Path<Id<Node>>,
    Form(form): Form<EditChild>,
) -> Result<Content<ChildNode>, ServiceError> {
    state
        .edit_child(node_id, form, Arc::new(operator))
        .await
        .map(|child| Content(child, accept))
}
