use axum::{
    extract::{Query, State},
    http::StatusCode,
    middleware,
    routing::get,
    Form, Json, Router,
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
    Form(form): Form<PostCoto>,
) -> Result<(StatusCode, Json<Coto>), ApiError> {
    if let Err(errors) = form.validate() {
        return ("coto", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        let (coto, changelog) = db.post_coto(
            &form.cotonoma_id.unwrap(), // validated to be Some
            None,                       // TODO: set node_id
            &form.content.unwrap(),     // validated to be Some
            form.summary.as_deref(),
        )?;
        state.publish_change(changelog)?;
        Ok((StatusCode::CREATED, Json(coto)))
    })
    .await?
}
