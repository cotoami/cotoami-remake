use axum::{
    extract::{Query, State},
    routing::get,
    Form, Json, Router,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    api::{ClientErrors, Pagination, WebError},
    AppState,
};

pub(super) fn routes() -> Router<AppState> {
    Router::new().route("/", get(recent_cotos).post(post_coto))
}

const DEFAULT_PAGE_SIZE: i64 = 30;

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<AppState>,
    Query(pagination): Query<Pagination>,
) -> Result<Json<Paginated<Coto>>, WebError> {
    if let Err(errors) = pagination.validate() {
        return ClientErrors::from_validation_errors("cotos", errors).into_result();
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
    #[validate(length(max = "Coto::CONTENT_MAX_LENGTH"))]
    content: String,

    #[validate(length(max = "Coto::SUMMARY_MAX_LENGTH"))]
    summary: Option<String>,

    cotonoma_id: Id<Cotonoma>,
}

async fn post_coto(
    State(state): State<AppState>,
    Form(form): Form<PostCoto>,
) -> Result<Json<Coto>, WebError> {
    if let Err(errors) = form.validate() {
        return ClientErrors::from_validation_errors("coto", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        let (coto, changelog) = db.post_coto(
            &form.cotonoma_id,
            None, // TODO: set node_id
            &form.content,
            form.summary.as_deref(),
        )?;
        state.publish_change(changelog)?;
        Ok(Json(coto))
    })
    .await?
}
