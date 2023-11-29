use accept_header::Accept;
use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    middleware,
    routing::get,
    Extension, Router, TypedHeader,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    service::{error::IntoServiceResult, models::Pagination, ServiceError},
    web::Content,
    NodeState,
};

mod cotos;

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotonomas))
        .route("/:cotonoma_id", get(get_cotonoma))
        .nest("/:cotonoma_id/cotos", cotos::routes())
        .layer(middleware::from_fn(super::require_operator))
        .layer(middleware::from_fn(super::require_session))
}

const DEFAULT_PAGE_SIZE: i64 = 100;

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotonomas
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotonomas(
    State(state): State<NodeState>,
    Extension(_operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<Paginated<Cotonoma>>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("cotonomas", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db().new_session()?;
        let cotonomas = db.recent_cotonomas(
            None,
            pagination.page_size.unwrap_or(DEFAULT_PAGE_SIZE),
            pagination.page,
        )?;
        Ok(Content(cotonomas, accept))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotonomas/:cotonoma_id
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct CotonomaDetails {
    cotonoma: Cotonoma,
    coto: Coto,
}

async fn get_cotonoma(
    State(state): State<NodeState>,
    Extension(_operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
) -> Result<Content<CotonomaDetails>, ServiceError> {
    spawn_blocking(move || {
        let mut db = state.db().new_session()?;
        let (cotonoma, coto) = db.cotonoma_or_err(&cotonoma_id)?;
        Ok(Content(CotonomaDetails { cotonoma, coto }, accept))
    })
    .await?
}
