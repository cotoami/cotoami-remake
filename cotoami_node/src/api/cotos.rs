use anyhow::{anyhow, Result};
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
    AppState,
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
        let mut db = state.db.create_session()?;
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

    let local_post: Result<Option<Coto>> = spawn_blocking(move || {
        let mut db = state.db.create_session()?;

        let cotonoma_id = form.cotonoma_id.unwrap(); // validated to be Some
        let (cotonoma, _) = db.get_cotonoma_or_err(&cotonoma_id)?;

        if db.is_local(&cotonoma) {
            let (coto, changelog) = db.post_coto(
                &form.content.unwrap(), // validated to be Some
                form.summary.as_deref(),
                &cotonoma,
                &operator,
            )?;
            state.publish_change(changelog)?;
            Ok(Some(coto))
        } else {
            Ok(None)
        }
    })
    .await?;

    if let Some(coto) = local_post? {
        Ok((StatusCode::CREATED, Json(coto)))
    } else {
        // send a request to one of the parents
        Err(anyhow!("Not yet implemented"))?
    }
}
