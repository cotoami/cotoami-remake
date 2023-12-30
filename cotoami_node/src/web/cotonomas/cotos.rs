use std::sync::Arc;

use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    routing::get,
    Extension, Form, Router, TypedHeader,
};
use cotoami_db::prelude::*;
use validator::Validate;

use crate::{
    service::{error::IntoServiceResult, models::Pagination, ServiceError},
    web::{Accept, Content},
    NodeState,
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new().route("/", get(recent_cotos).post(post_coto))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotonomas/:cotonoma_id/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<NodeState>,
    Extension(_operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<Paginated<Coto>>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("cotos", errors).into_result();
    }
    state
        .recent_cotos(Some(cotonoma_id), pagination)
        .await
        .map(|cotos| Content(cotos, accept))
        .map_err(ServiceError::from)
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/cotonomas/:cotonoma_id/cotos
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct PostCoto {
    #[validate(required, length(max = "Coto::CONTENT_MAX_LENGTH"))]
    content: Option<String>,

    #[validate(length(max = "Coto::SUMMARY_MAX_LENGTH"))]
    summary: Option<String>,
}

async fn post_coto(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Form(form): Form<PostCoto>,
) -> Result<(StatusCode, Content<Coto>), ServiceError> {
    if let Err(errors) = form.validate() {
        return ("coto", errors).into_result();
    }
    state
        .post_coto(
            form.content.unwrap_or_else(|| unreachable!()),
            form.summary,
            cotonoma_id,
            Arc::new(operator),
        )
        .await
        .map(|coto| (StatusCode::CREATED, Content(coto, accept)))
        .map_err(ServiceError::from)
}
