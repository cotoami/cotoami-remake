use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    routing::get,
    Extension, Form, Router, TypedHeader,
};
use cotoami_db::prelude::*;
use serde_json::json;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    service::{
        error::{IntoServiceResult, RequestError},
        models::Pagination,
        ServiceError,
    },
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
        .map(|x| Content(x, accept))
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
    spawn_blocking(move || {
        let mut ds = state.db().new_session()?;

        // Check if the cotonoma belongs to this node
        let (cotonoma, _) = ds.cotonoma_or_err(&cotonoma_id)?;
        if !state.db().globals().is_local(&cotonoma) {
            return RequestError::new("not-for-this-node")
                .with_param("cotonoma_name", json!(cotonoma.name))
                .into_result();
        }

        // Post a coto
        let (coto, change) = ds.post_coto(
            &form.content.unwrap_or_else(|| unreachable!()),
            form.summary.as_deref(),
            &cotonoma,
            &operator,
        )?;
        state.pubsub().publish_change(change);

        Ok((StatusCode::CREATED, Content(coto, accept)))
    })
    .await?
}
