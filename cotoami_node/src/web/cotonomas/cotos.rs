use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    middleware,
    routing::get,
    Extension, Form, Json, Router,
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
    web::require_session,
    NodeState,
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotos).post(post_coto))
        .layer(middleware::from_fn(require_session))
}

const DEFAULT_PAGE_SIZE: i64 = 30;

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotonomas/:cotonoma_id/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<NodeState>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Query(pagination): Query<Pagination>,
) -> Result<Json<Paginated<Coto>>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("cotos", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db().new_session()?;
        let cotos = db.recent_cotos(
            None,
            Some(&cotonoma_id),
            pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
            pagination.page,
        )?;
        Ok(Json(cotos))
    })
    .await?
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
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Extension(operator): Extension<Operator>,
    Form(form): Form<PostCoto>,
) -> Result<(StatusCode, Json<Coto>), ServiceError> {
    if let Err(errors) = form.validate() {
        return ("coto", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db().new_session()?;

        // Check if the cotonoma belongs to this node
        let (cotonoma, _) = db.cotonoma_or_err(&cotonoma_id)?;
        if !db.is_local(&cotonoma) {
            return RequestError::new("not-for-this-node")
                .with_param("cotonoma_name", json!(cotonoma.name))
                .into_result();
        }

        // Post a coto
        let (coto, change) = db.post_coto(
            &form.content.unwrap_or_else(|| unreachable!()),
            form.summary.as_deref(),
            &cotonoma,
            &operator,
        )?;
        state.pubsub().publish_change(change);

        Ok((StatusCode::CREATED, Json(coto)))
    })
    .await?
}
