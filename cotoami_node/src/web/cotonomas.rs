use anyhow::Result;
use axum::{
    extract::{Path, Query, State},
    middleware,
    routing::get,
    Router, TypedHeader,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    service::{error::IntoServiceResult, models::Pagination, ServiceError},
    state::NodeState,
    web::{Accept, Content},
};

mod cotos;

pub(crate) use cotos::PostCoto;

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotonomas))
        .route("/:cotonoma_id", get(get_cotonoma))
        .nest("/:cotonoma_id/cotos", cotos::routes())
        .layer(middleware::from_fn(super::require_operator))
        .layer(middleware::from_fn(super::require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/cotonomas
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotonomas(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Query(pagination): Query<Pagination>,
) -> Result<Content<Paginated<Cotonoma>>, ServiceError> {
    if let Err(errors) = pagination.validate() {
        return ("cotonomas", errors).into_result();
    }
    state
        .recent_cotonomas(None, pagination)
        .await
        .map(|cotonomas| Content(cotonomas, accept))
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
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
) -> Result<Content<CotonomaDetails>, ServiceError> {
    spawn_blocking(move || {
        let mut db = state.db().new_session()?;
        let (cotonoma, coto) = db.try_get_cotonoma(&cotonoma_id)?;
        Ok(Content(CotonomaDetails { cotonoma, coto }, accept))
    })
    .await?
}
