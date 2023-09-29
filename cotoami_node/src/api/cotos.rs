use anyhow::Result;
use axum::{
    extract::{Query, State},
    http::StatusCode,
    middleware,
    routing::get,
    Extension, Form, Json, Router,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    api::Pagination,
    error::{ApiError, IntoApiResult},
    AppState, ChangePub,
};

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", get(recent_cotos).post(post_coto))
        .layer(middleware::from_fn(super::require_session))
}

const DEFAULT_PAGE_SIZE: i64 = 30;

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<AppState>,
    Query(pagination): Query<Pagination>,
) -> Result<Json<Paginated<Coto>>, ApiError> {
    if let Err(errors) = pagination.validate() {
        return ("cotos", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.new_session()?;
        let cotos = db.recent_cotos(
            None,
            None,
            pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
            pagination.page,
        )?;
        Ok(Json(cotos))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/cotos
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct PostCoto {
    #[validate(required, length(max = "Coto::CONTENT_MAX_LENGTH"))]
    content: Option<String>,

    #[validate(length(max = "Coto::SUMMARY_MAX_LENGTH"))]
    summary: Option<String>,

    #[validate(required)]
    cotonoma_id: Option<Id<Cotonoma>>,
}

async fn post_coto(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Form(form): Form<PostCoto>,
) -> Result<(StatusCode, Json<Coto>), ApiError> {
    if let Err(errors) = form.validate() {
        return ("coto", errors).into_result();
    }

    let local_post = spawn_blocking(move || {
        let mut db = state.db.new_session()?;

        let cotonoma_id = form.cotonoma_id.unwrap_or_else(|| unreachable!());
        let (cotonoma, _) = db.cotonoma_or_err(&cotonoma_id)?;

        if db.is_local(&cotonoma) {
            let (coto, change) = db.post_coto(
                &form.content.unwrap_or_else(|| unreachable!()),
                form.summary.as_deref(),
                &cotonoma,
                &operator,
            )?;
            state.pubsub.lock().publish_change(change)?;
            Ok::<_, ApiError>(Some(coto))
        } else {
            Ok::<_, ApiError>(None)
        }
    })
    .await??;

    if let Some(coto) = local_post {
        Ok((StatusCode::CREATED, Json(coto)))
    } else {
        // send a request to one of the parents
        unimplemented!();
    }
}
